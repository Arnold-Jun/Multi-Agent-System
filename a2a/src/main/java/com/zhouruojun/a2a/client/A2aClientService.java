package com.zhouruojun.a2a.client;

import com.zhouruojun.a2acore.client.A2AClient;
import com.zhouruojun.a2acore.spec.TaskSendParams;
import com.zhouruojun.a2acore.spec.message.SendTaskResponse;
import com.zhouruojun.a2acore.spec.message.SendTaskStreamingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * A2A�ͻ��˷�??
 * �ṩA2AClient��ͳһ�����͵��ý�??
 */
@Service
@Slf4j
public class A2aClientService {
    
    @Autowired
    private List<A2AClient> a2aClients;
    
    /**
     * �������������ƻ�ȡA2AClient
     */
    public A2AClient getClient(String agentName) {
        return a2aClients.stream()
            .filter(client -> client.getAgentCard().getName().equals(agentName))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No A2AClient found for agent: " + agentName));
    }
    
    /**
     * ͬ����������??
     */
    public SendTaskResponse invokeAgent(String agentName, TaskSendParams params) {
        try {
            A2AClient client = getClient(agentName);
            log.info("Invoking agent {} with task {}", agentName, params.getId());
            return client.sendTask(params);
        } catch (Exception e) {
            log.error("Failed to invoke agent: {}", agentName, e);
            throw new RuntimeException("Failed to invoke agent: " + agentName, e);
        }
    }
    
    /**
     * ��ʽ��������??
     */
    public Flux<SendTaskStreamingResponse> invokeAgentStreaming(String agentName, TaskSendParams params) {
        try {
            A2AClient client = getClient(agentName);
            log.info("Invoking agent {} streaming with task {}", agentName, params.getId());
            return client.sendTaskSubscribe(params);
        } catch (Exception e) {
            log.error("Failed to invoke agent streaming: {}", agentName, e);
            return Flux.error(new RuntimeException("Failed to invoke agent streaming: " + agentName, e));
        }
    }
    
    /**
     * ��ȡ���п��õ�����??
     */
    public List<A2AClient> getAllClients() {
        return a2aClients;
    }
}

