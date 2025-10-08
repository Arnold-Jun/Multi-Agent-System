package com.xiaohongshu.codewiz.codewizagent.cragent.mcp.discovery;

import static com.github.phantomthief.concurrent.MoreFutures.getUnchecked;

import com.xiaohongshu.infra.rpc.client.governance.XDSClientGovernanceProvider;
import com.xiaohongshu.infra.rpc.utils.SubscriberFactory2;
import com.xiaohongshu.infra.xds.Protocol;
import com.xiaohongshu.infra.xds.Subscriber;
import com.xiaohongshu.infra.xds.SubscriberFactory;
import com.xiaohongshu.infra.xds.WatchKey;
import com.xiaohongshu.infra.xds.eds.ParsableLoadAssignment;
import com.xiaohongshu.infra.xrpc.loadbalance.DirectHostSubscriber;
import com.xiaohongshu.infra.xrpc.loadbalance.LoadAssignmentHelper;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * 服务发现拉取任务
 * </p>
 *
 * @author 瑞诺
 * create on 2024/6/25 21:31
 */
@Slf4j
public class McpServiceDiscoveryPollTask implements Runnable {

    private McpServiceDiscovery mcpServiceDiscovery;

    public McpServiceDiscoveryPollTask(McpServiceDiscovery mcpServiceDiscovery) {
        this.mcpServiceDiscovery = mcpServiceDiscovery;
    }

    private Map<String, Subscriber<ParsableLoadAssignment>> subscribersMap = new ConcurrentHashMap<>();

    @Override
    public void run() {

        try {
            for (String serviceName : mcpServiceDiscovery.getServiceNames()) {
                init(serviceName);
            }

            while (true) {
                try {
                    subscribersMap.forEach((mcpServiceName, subscriber) -> {
                        //使用方需要定时检查实例列表有没有变更，可以使用下面的方式
                        WatchKey watchKey = WatchKey.createEDSOutboundKey(mcpServiceName, Protocol.THRIFT);
                        ParsableLoadAssignment update = getUnchecked(subscriber.read(watchKey), Duration.ofSeconds(5));
                        //实例发生变化，更新路由表
                        if (!update.getRaw().equals(mcpServiceDiscovery.getParseableLoadAssignment().getRaw())) {
                            mcpServiceDiscovery.setParseableLoadAssignment(mcpServiceName, update);
                        }
                    });
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    throw ie;
                } catch (Exception e) {
                    log.error("McpServiceDiscoveryRunnable while is exception.", e);
                }
            }
        } catch (InterruptedException ie) {
            log.error("McpServiceDiscoveryRunnable is interrupted.", ie);
            Thread.currentThread().interrupt(); // 重新设置中断状态
        } catch (Exception e) {
            log.error("McpServiceDiscoveryRunnable is exception.", e);
        }

    }

    public void init(String serviceName) {
        log.info("McpServiceDiscoveryRunnable start... serviceName: {}", serviceName);
        Subscriber<ParsableLoadAssignment> subscriber = SubscriberFactory.getParsableEDSSubscriber();
        WatchKey watchKey = WatchKey.createEDSOutboundKey(serviceName, Protocol.THRIFT);
        ParsableLoadAssignment initParseable = getUnchecked(subscriber.read(watchKey), Duration.ofSeconds(5));
        mcpServiceDiscovery.setParseableLoadAssignment(serviceName, initParseable);
        subscribersMap.put(serviceName, subscriber);
    }
}
