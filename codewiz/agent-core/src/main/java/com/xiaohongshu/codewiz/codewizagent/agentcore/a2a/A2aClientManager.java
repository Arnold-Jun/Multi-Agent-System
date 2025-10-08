package com.xiaohongshu.codewiz.codewizagent.agentcore.a2a;

import cn.hutool.core.collection.CollectionUtil;
import com.google.common.net.HostAndPort;
import com.xiaohongshu.codewiz.a2a.client.autoconfiguration.A2aAgentsProperties;
import com.xiaohongshu.codewiz.a2acore.client.A2AClient;
import com.xiaohongshu.codewiz.a2acore.client.AgentCardResolver;
import com.xiaohongshu.codewiz.a2acore.client.sse.SseEventHandler;
import com.xiaohongshu.codewiz.a2acore.spec.AgentCard;
import com.xiaohongshu.codewiz.a2acore.spec.PushNotificationConfig;
import com.xiaohongshu.codewiz.a2acore.spec.TaskSendParams;
import com.xiaohongshu.codewiz.a2acore.spec.message.SendTaskStreamingResponse;
import com.xiaohongshu.codewiz.a2acore.spec.message.util.Util;
import com.xiaohongshu.codewiz.codewizagent.agentcore.discovery.RoundLoadBalance;
import com.xiaohongshu.codewiz.codewizagent.agentcore.discovery.ServiceDiscovery;
import com.xiaohongshu.infra.xds.eds.Instance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.rowset.serial.SerialException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/14 10:37
 */
@Slf4j
@Component
public class A2aClientManager {

//    @Resource
    private List<A2AClient> clients;

    @Resource
    private ServiceDiscovery serviceDiscovery;

    @Resource
    private A2aAgentsProperties a2aAgentsProperties;

    @Resource
    private List<SseEventHandler> sseEventHandlers;

    private Map<String, List<A2AClient>> clientsMap;

    private List<String> supportModes = Arrays.asList("text", "file", "data");

    private RoundLoadBalance<A2AClient> loadBalance;

    @PostConstruct
    public void init() {
        loadBalance = new RoundLoadBalance<>();
        clientsMap = new ConcurrentHashMap<>();
        clients = new CopyOnWriteArrayList<>();

        // 先固定一下当前的CrAgent
        A2aRegister a2aRegister = new A2aRegister();
        a2aRegister.setName("CrAgent");
        a2aRegister.setBaseUrl("http://127.0.0.1:8089/");
        register(a2aRegister);

//        for (A2AClient client : clients) {
//            clientsMap.computeIfAbsent(client.getAgentCard().getName(), k -> new CopyOnWriteArrayList<>()).add(client);
//        }
    }

    public A2AClient getA2aClient(String agentName) {
        List<A2AClient> a2aClients = clientsMap.get(agentName);
        if (CollectionUtil.isEmpty(a2aClients)) {
            throw new RuntimeException("No A2AClient found for agent: " + agentName);
        }
        return loadBalance.next(agentName, a2aClients);
    }

    public AgentCard register(A2aRegister register) {
        List<String> urls = new ArrayList<>();
        if (register.getBaseUrl().startsWith("eds://")) {
            String baseUrl = register.getBaseUrl();
            String serviceName = baseUrl.substring("eds://".length());
            serviceDiscovery.addServiceName(serviceName);
            List<Instance> services = serviceDiscovery.getServices(serviceName, "", "");
            services.forEach(instance -> {
                HostAndPort hostAndPort = instance.getHostAndPort();
                String url = hostAndPort.getHost() + ":" + hostAndPort.getPort() + "/";
                if (!url.startsWith("http")) {
                    url = "http://" + url;
                }
                urls.add(url);
            });
        } else {
            urls.add(register.getBaseUrl());
        }

        PushNotificationConfig notification = register.getNotification();

        AgentCardResolver resolver = new AgentCardResolver(register.getName(), register.getBaseUrl(), register.getAgentCardPath());
        try {
            AgentCard agentCard = resolver.resolve();
            A2AClient a2AClient =
                    new A2AClient(agentCard, sseEventHandlers, notification != null ? notification : a2aAgentsProperties.getNotification());
            clients.add(a2AClient);
            clientsMap.computeIfAbsent(a2AClient.getAgentCard().getName(), k -> new CopyOnWriteArrayList<>()).add(a2AClient);
            return a2AClient.getAgentCard();
        } catch (Exception ex) {
            log.error("agent resolve exception, agent: {}, error: {}", Util.toJson(resolver), ex.getMessage());
            throw new RuntimeException("Failed to register agent: " + register.getName(), ex);
        }
    }

    public List<AgentCard> getAgentCards() {
        return clients.stream().map(A2AClient::getAgentCard).collect(Collectors.toList());
    }

    public void sendTaskSubscribe(String agentName, TaskSendParams taskSendParams) {
        A2AClient a2aClient = getA2aClient(agentName);
        Flux<SendTaskStreamingResponse> responseFlux = a2aClient.sendTaskSubscribe(taskSendParams);
        responseFlux.subscribe();
    }

    public List<String> getSystemSupportModes() {
        return supportModes;
    }

}
