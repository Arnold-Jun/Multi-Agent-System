package com.zhouruojun.agentcore.a2a;

import org.springframework.http.HttpStatus;

import cn.hutool.core.collection.CollectionUtil;
import com.zhouruojun.agentcore.client.A2AClient;
import com.zhouruojun.agentcore.client.AgentCardResolver;
import com.zhouruojun.agentcore.client.sse.SseEventHandler;
import com.zhouruojun.agentcore.config.AgentConstants;
import com.zhouruojun.agentcore.spec.AgentCard;
import com.zhouruojun.agentcore.spec.PushNotificationConfig;
import com.zhouruojun.agentcore.spec.TaskSendParams;
import com.zhouruojun.agentcore.spec.message.SendTaskStreamingResponse;
import com.zhouruojun.agentcore.spec.message.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * A2A客户端管理器 - 完整版本
 * 支持智能体注册、发现、健康检查和A2A协议通信
 */
@Slf4j
@Component
public class A2aClientManager {

    private final Map<String, String> agentRegistry = new ConcurrentHashMap<>();
    private List<A2AClient> clients;
    private Map<String, List<A2AClient>> clientsMap;
    private final List<String> supportModes = Arrays.asList("text", "file", "data");

    @Autowired
    private ApplicationContext applicationContext;

    // 暂时移除SseEventHandler依赖，避免启动错误
    // @Resource
    // private List<SseEventHandler> sseEventHandlers;

    @PostConstruct
    public void initializeDefaultAgents() {
        clientsMap = new ConcurrentHashMap<>();
        clients = new CopyOnWriteArrayList<>();

        try {
            A2aRegister a2aRegister = new A2aRegister();
            a2aRegister.setName(AgentConstants.DATA_ANALYSIS_AGENT_NAME);
            a2aRegister.setBaseUrl(AgentConstants.DATA_ANALYSIS_AGENT_URL);
            a2aRegister.setAgentCardPath(AgentConstants.DATA_ANALYSIS_AGENT_CARD_PATH);
            register(a2aRegister);
            log.info("Successfully registered {} at {}", AgentConstants.DATA_ANALYSIS_AGENT_NAME, AgentConstants.DATA_ANALYSIS_AGENT_URL);
        } catch (Exception e) {
            log.warn("Failed to register {} during startup: {}. Will retry later.", AgentConstants.DATA_ANALYSIS_AGENT_NAME, e.getMessage());
        }

        log.info("Initialized agents for distributed mode - {} at {}", AgentConstants.DATA_ANALYSIS_AGENT_NAME, AgentConstants.DATA_ANALYSIS_AGENT_URL);
    }

    public void registerAgent(String agentName, String agentUrl) {
        agentRegistry.put(agentName, agentUrl);
        log.info("Registered agent: {} -> {}", agentName, agentUrl);
    }

    public AgentCard register(A2aRegister register) {
        PushNotificationConfig notification = register.getNotification();

        AgentCardResolver resolver = new AgentCardResolver(register.getName(), register.getBaseUrl(), register.getAgentCardPath());
        try {
            AgentCard agentCard = resolver.resolve();
            A2AClient a2AClient = new A2AClient(agentCard, null, notification, WebClient.create());
            clients.add(a2AClient);
            clientsMap.computeIfAbsent(a2AClient.getAgentCard().getName(), k -> new CopyOnWriteArrayList<>()).add(a2AClient);

            agentRegistry.put(register.getName(), register.getBaseUrl());

            return a2AClient.getAgentCard();
        } catch (Exception ex) {
            log.error("agent resolve exception, agent: {}, error: {}", Util.toJson(resolver), ex.getMessage());
            throw new RuntimeException("Failed to register agent: " + register.getName(), ex);
        }
    }

    public A2AClient getA2aClient(String agentName) {
        List<A2AClient> a2aClients = clientsMap.get(agentName);
        if (CollectionUtil.isEmpty(a2aClients)) {
            throw new RuntimeException("No A2AClient found for agent: " + agentName);
        }
        return a2aClients.get(0);
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

    public String getAgentUrl(String agentName) {
        return agentRegistry.get(agentName);
    }

    public Map<String, String> getAllAgents() {
        return new ConcurrentHashMap<>(agentRegistry);
    }

    public List<String> getAgentNames() {
        return List.copyOf(agentRegistry.keySet());
    }

    public boolean checkAgentHealth(String agentName) {
        try {
            A2AClientService a2aClientService = applicationContext.getBean(A2AClientService.class);
            return a2aClientService.checkAgentHealth(agentName, getAgentUrl(agentName));
        } catch (Exception e) {
            log.warn("Health check failed for agent: {}", agentName, e);
            return false;
        }
    }

    public A2AInvocationResult invokeAgent(String agentName,
                                           String taskType,
                                           Map<String, Object> parameters,
                                           String sessionId) {
        try {
            String agentUrl = getAgentUrl(agentName);
            if (agentUrl == null && AgentConstants.DATA_ANALYSIS_AGENT_NAME.equals(agentName)) {
                log.warn("Agent {} not found in registry, attempting to re-register", agentName);
                try {
                    A2aRegister a2aRegister = new A2aRegister();
                    a2aRegister.setName(AgentConstants.DATA_ANALYSIS_AGENT_NAME);
                    a2aRegister.setBaseUrl(AgentConstants.DATA_ANALYSIS_AGENT_URL);
                    register(a2aRegister);
                    agentUrl = getAgentUrl(agentName);
                } catch (Exception e) {
                    log.error("Failed to re-register {}: {}", AgentConstants.DATA_ANALYSIS_AGENT_NAME, e.getMessage());
                    return A2AInvocationResult.failure(
                            "数据分析智能体服务不可用，请确保服务已启动在端口8082",
                            HttpStatus.SERVICE_UNAVAILABLE.value());
                }
            }

            if (agentUrl == null) {
                return A2AInvocationResult.failure("未找到智能体 " + agentName, HttpStatus.NOT_FOUND.value());
            }

            A2AClientService a2aClientService = applicationContext.getBean(A2AClientService.class);
            return a2aClientService.invokeAgent(agentName, taskType, parameters, sessionId, agentUrl);
        } catch (Exception e) {
            log.error("Failed to invoke agent: {}", agentName, e);
            return A2AInvocationResult.failure("未知异常: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    public String getAgentInfo(String agentName) {
        try {
            A2AClientService a2aClientService = applicationContext.getBean(A2AClientService.class);
            return a2aClientService.getAgentInfo(agentName, getAgentUrl(agentName));
        } catch (Exception e) {
            log.error("Failed to get agent info: {}", agentName, e);
            return "Error: " + e.getMessage();
        }
    }
}
