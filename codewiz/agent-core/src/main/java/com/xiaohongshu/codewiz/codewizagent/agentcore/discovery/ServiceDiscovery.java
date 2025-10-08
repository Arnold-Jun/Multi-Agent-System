package com.xiaohongshu.codewiz.codewizagent.agentcore.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Table;
import com.xiaohongshu.infra.xds.eds.Instance;
import com.xiaohongshu.infra.xds.eds.InstanceList;
import com.xiaohongshu.infra.xds.eds.ParsableLoadAssignment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * <p>
 * 服务发现
 * </p>
 *
 * @author 瑞诺
 * create on 2024/6/25 21:13
 */
@Slf4j
@Component
public class ServiceDiscovery {

    private ConcurrentHashMap<String, AtomicReference<ConcurrentHashMap<String, ConcurrentHashMap<String, InstanceList>>>> instanceListTableRefMap =
            new ConcurrentHashMap<>();

    private List<String> serviceNames;

    private List<Instance> empty = new ArrayList<>();

    private ParsableLoadAssignment parseableLoadAssignment;

    private Thread domainThread;

    private ServiceDiscoveryPollTask serviceDiscoveryPollTask;

    private DiscoveryLoadBalance<Instance> loadBalance;

    private ObjectMapper objectMapper;

    public ServiceDiscovery(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> getServiceNames() {
        return serviceNames;
    }

    /**
     * 添加新的服务
     * @param serviceName
     */
    public void addServiceName(String serviceName) {
        serviceNames.add(serviceName);
        instanceListTableRefMap.computeIfAbsent(serviceName, k -> new AtomicReference<>());
        serviceDiscoveryPollTask.init(serviceName);
    }

    @PostConstruct
    public void init() {
        // 先初始化一个轮训的负载均衡器
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        loadBalance = new RoundLoadBalance<>();
        serviceNames = new CopyOnWriteArrayList<>();
        if (domainThread != null && domainThread.isAlive()) {
            return;
        }
        serviceDiscoveryPollTask = new ServiceDiscoveryPollTask(this);
        domainThread = new Thread(serviceDiscoveryPollTask);
        domainThread.setDaemon(true);
        domainThread.setName("ServiceDiscoveryRunnable");
        domainThread.start();
    }

    public void setParseableLoadAssignment(String mcpServiceName, ParsableLoadAssignment parseableLoadAssignment) {
        this.parseableLoadAssignment = parseableLoadAssignment;
        setInstanceListTable(mcpServiceName, parseableLoadAssignment.getParsed());
    }

    public ParsableLoadAssignment getParseableLoadAssignment() {
        return this.parseableLoadAssignment;
    }

    public Instance getServiceByDefaultZone(String serviceName) {
        return getService(serviceName, "", "");
    }

    public Instance getService(String serviceName, String shard, String swimlane) {
        return loadBalance.next(serviceName, getServices(serviceName, shard, swimlane));
    }

    public List<Instance> getServices(String serviceName, String shard, String swimlane) {
        ConcurrentHashMap<String, InstanceList> stringInstanceListConcurrentHashMap = instanceListTableRefMap.get(serviceName).get().get(shard);
        InstanceList instanceList = !CollectionUtils.isEmpty(stringInstanceListConcurrentHashMap)
                ? stringInstanceListConcurrentHashMap.get(swimlane) : null;
        return instanceList != null ? instanceList.getPriorityFirstInstances() : empty;
    }

    public List<Instance> getAllServices(String serviceName, String shard, String swimlane) {
        ConcurrentHashMap<String, InstanceList> stringInstanceListConcurrentHashMap = instanceListTableRefMap.get(serviceName).get().get(shard);
        InstanceList instanceList = !CollectionUtils.isEmpty(stringInstanceListConcurrentHashMap)
                ? stringInstanceListConcurrentHashMap.get(swimlane) : null;
        return instanceList != null ? instanceList.getInstances() : empty;
    }

    public void setInstanceListTable(String serviceName, Table<String, String, InstanceList> instanceListTable) {
        // copy on write
        ConcurrentHashMap<String, ConcurrentHashMap<String, InstanceList>> newTable = new ConcurrentHashMap<>();
        for (String shard : instanceListTable.rowKeySet()) {
            ConcurrentHashMap<String, InstanceList> map = new ConcurrentHashMap<>();
            for (String swimlane : instanceListTable.columnKeySet()) {
                map.put(swimlane, instanceListTable.get(shard, swimlane));
            }
            newTable.put(shard, map);
        }
        try {
            log.info("update instanceListTable:{}", objectMapper.writeValueAsString(newTable));
        } catch (Exception e) {}
        instanceListTableRefMap.get(serviceName).set(newTable);
    }
}
