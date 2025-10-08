package com.zhouruojun.agentcore.a2a;

import cn.hutool.core.collection.CollectionUtil;
import com.zhouruojun.a2a.client.autoconfiguration.A2aAgentsProperties;
import com.zhouruojun.a2acore.client.A2AClient;
import com.zhouruojun.a2acore.client.AgentCardResolver;
import com.zhouruojun.a2acore.client.sse.SseEventHandler;
import com.zhouruojun.a2acore.spec.AgentCard;
import com.zhouruojun.a2acore.spec.PushNotificationConfig;
import com.zhouruojun.a2acore.spec.TaskSendParams;
import com.zhouruojun.a2acore.spec.TaskState;
import com.zhouruojun.a2acore.spec.TaskArtifactUpdateEvent;
import com.zhouruojun.a2acore.spec.TaskStatusUpdateEvent;
import com.zhouruojun.a2acore.spec.message.SendTaskStreamingResponse;
import com.zhouruojun.a2acore.spec.message.util.Util;
import com.zhouruojun.agentcore.config.AgentConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * A2A客户端管理器
 * 支持智能体注册、发现、健康检查和A2A协议通信
 */
@Slf4j
@Component
public class A2aClientManager implements ApplicationContextAware {

    private final Map<String, String> agentRegistry = new ConcurrentHashMap<>();
    private List<A2AClient> clients;
    private Map<String, List<A2AClient>> clientsMap;
    private final List<String> supportModes = Arrays.asList("text", "file", "data");

    @Autowired(required = false)
    private A2aAgentsProperties a2aAgentsProperties;

    private List<SseEventHandler> sseEventHandlers = new ArrayList<>();
    private ApplicationContext applicationContext;

    @PostConstruct
    public void initializeDefaultAgents() {
        clientsMap = new ConcurrentHashMap<>();
        clients = new CopyOnWriteArrayList<>();
        
        // 延迟初始化SSE事件处理器
        initializeSseEventHandlers();

        try {
                // 注册数据分析智能体
                A2aRegister a2aRegister = new A2aRegister();
                a2aRegister.setName(AgentConstants.DATA_ANALYSIS_AGENT_NAME);
                a2aRegister.setBaseUrl(AgentConstants.DATA_ANALYSIS_AGENT_URL);
                a2aRegister.setAgentCardPath(AgentConstants.DATA_ANALYSIS_AGENT_CARD_PATH);
                register(a2aRegister);
                log.info("Successfully registered {} at {}", AgentConstants.DATA_ANALYSIS_AGENT_NAME, AgentConstants.DATA_ANALYSIS_AGENT_URL);
            } catch (Exception e) {
                log.warn("Failed to register {} during startup: {}. Will retry later.", AgentConstants.DATA_ANALYSIS_AGENT_NAME, e.getMessage());
            }

            try {
                // 注册求职智能体
                A2aRegister jobSearchRegister = new A2aRegister();
                jobSearchRegister.setName(AgentConstants.JOB_SEARCH_AGENT_NAME);
                jobSearchRegister.setBaseUrl(AgentConstants.JOB_SEARCH_AGENT_URL);
                jobSearchRegister.setAgentCardPath(AgentConstants.JOB_SEARCH_AGENT_CARD_PATH);
                register(jobSearchRegister);
                log.info("Successfully registered {} at {}", AgentConstants.JOB_SEARCH_AGENT_NAME, AgentConstants.JOB_SEARCH_AGENT_URL);
            } catch (Exception e) {
                log.warn("Failed to register {} during startup: {}. Will retry later.", AgentConstants.JOB_SEARCH_AGENT_NAME, e.getMessage());
            }

            log.info("Initialized agents for distributed mode");
    }
    
    /**
     * 延迟初始化SSE事件处理器
     */
    private void initializeSseEventHandlers() {
        try {
            if (applicationContext != null) {
                Map<String, SseEventHandler> handlerBeans = applicationContext.getBeansOfType(SseEventHandler.class);
                sseEventHandlers.addAll(handlerBeans.values());
                log.info("Initialized {} SSE event handlers", sseEventHandlers.size());
            }
        } catch (Exception e) {
            log.warn("Failed to initialize SSE event handlers: {}", e.getMessage());
        }
    }
    
    @Override
    public void setApplicationContext(@org.springframework.lang.NonNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void registerAgent(String agentName, String agentUrl) {
        agentRegistry.put(agentName, agentUrl);
        log.info("Registered agent: {} -> {}", agentName, agentUrl);
    }

    public AgentCard register(A2aRegister register) {
        PushNotificationConfig notification = register.getNotification();
        if (notification == null && a2aAgentsProperties != null) {
            notification = a2aAgentsProperties.getNotification();
        }
        
        log.info("Registering agent {} with push notification config: {}", 
            register.getName(), notification != null ? notification.getUrl() : "null");

        AgentCardResolver resolver = new AgentCardResolver(register.getName(), register.getBaseUrl(), register.getAgentCardPath());
        try {
            AgentCard agentCard = resolver.resolve();
            String baseUrl = agentCard.getUrl() != null ? agentCard.getUrl() : register.getBaseUrl();
            WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
            A2AClient a2AClient = new A2AClient(agentCard, sseEventHandlers, notification, webClient);
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
            log.warn("No A2AClient found for agent: {}, attempting to register...", agentName);
            // 尝试重新注册 - 最多重试3次
            for (int retry = 0; retry < 3; retry++) {
                try {
                    log.info("Attempting to register agent: {} (retry {}/{})", agentName, retry + 1, 3);
                    
                    if (AgentConstants.DATA_ANALYSIS_AGENT_NAME.equals(agentName)) {
                        A2aRegister a2aRegister = new A2aRegister();
                        a2aRegister.setName(AgentConstants.DATA_ANALYSIS_AGENT_NAME);
                        a2aRegister.setBaseUrl(AgentConstants.DATA_ANALYSIS_AGENT_URL);
                        a2aRegister.setAgentCardPath(AgentConstants.DATA_ANALYSIS_AGENT_CARD_PATH);
                        
                        AgentCard agentCard = register(a2aRegister);
                        log.info("Successfully re-registered {} at {}, agentCard: {}", 
                                agentName, AgentConstants.DATA_ANALYSIS_AGENT_URL, agentCard.getName());
                        
                        // 重新获取 - 需要再次检查，因为register可能重写了AgentCard的name
                        String actualAgentName = agentCard.getName();
                        a2aClients = clientsMap.get(actualAgentName);
                        
                        if (!CollectionUtil.isEmpty(a2aClients)) {
                            return a2aClients.get(0);
                        }
                        
                        // 如果没有找到，也尝试原来的agentName（兼容性）
                        a2aClients = clientsMap.get(agentName);
                        if (!CollectionUtil.isEmpty(a2aClients)) {
                            return a2aClients.get(0);
                        }
                    } else if (AgentConstants.JOB_SEARCH_AGENT_NAME.equals(agentName)) {
                        A2aRegister jobSearchRegister = new A2aRegister();
                        jobSearchRegister.setName(AgentConstants.JOB_SEARCH_AGENT_NAME);
                        jobSearchRegister.setBaseUrl(AgentConstants.JOB_SEARCH_AGENT_URL);
                        jobSearchRegister.setAgentCardPath(AgentConstants.JOB_SEARCH_AGENT_CARD_PATH);
                        
                        AgentCard agentCard = register(jobSearchRegister);
                        log.info("Successfully re-registered {} at {}, agentCard: {}", 
                                agentName, AgentConstants.JOB_SEARCH_AGENT_URL, agentCard.getName());
                        
                        // 重新获取 - 需要再次检查，因为register可能重写了AgentCard的name
                        String actualAgentName = agentCard.getName();
                        a2aClients = clientsMap.get(actualAgentName);
                        
                        if (!CollectionUtil.isEmpty(a2aClients)) {
                            return a2aClients.get(0);
                        }
                        
                        // 如果没有找到，也尝试原来的agentName（兼容性）
                        a2aClients = clientsMap.get(agentName);
                        if (!CollectionUtil.isEmpty(a2aClients)) {
                            return a2aClients.get(0);
                        }
                    }
                    
                    // 如果还没找到，等待一下再重试
                    if (retry < 2) {
                        Thread.sleep(1000 + retry * 1000); // 1秒, 2秒, 3秒... 递增等待
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to re-register agent: {} (retry {}/{})", agentName, retry + 1, 3, e);
                    if (retry == 2) { // 最后一次重试失败
                        throw new RuntimeException("Failed to re-register agent after 3 attempts: " + agentName, e);
                    }
                }
            }
            throw new RuntimeException("No A2AClient found for agent after all retries: " + agentName);
        }
        return a2aClients.get(0); // 简单轮询，后续可扩展为负载均衡
    }

    public List<AgentCard> getAgentCards() {
        return clients.stream().map(A2AClient::getAgentCard).collect(Collectors.toList());
    }

    public void sendTaskSubscribe(String agentName, TaskSendParams taskSendParams) {
        A2AClient a2aClient = getA2aClient(agentName);
        Flux<SendTaskStreamingResponse> responseFlux = a2aClient.sendTaskSubscribe(taskSendParams);
        responseFlux.subscribe();
    }

    /**
     * 发送任务订阅并等待响应
     */
    public String sendTaskSubscribeAndWait(String agentName, TaskSendParams taskSendParams, String sessionId) {
        A2AClient a2aClient = getA2aClient(agentName);
        
        // 创建一个同步阻塞机制
        CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AtomicReference<String> responseRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();
        
        Flux<SendTaskStreamingResponse> responseFlux = a2aClient.sendTaskSubscribe(taskSendParams);
        responseFlux.subscribe(
            response -> {
                try {
                    if (response.getResult() instanceof TaskStatusUpdateEvent) {
                        TaskStatusUpdateEvent statusEvent = (TaskStatusUpdateEvent) response.getResult();
                        if (statusEvent.isFinalFlag() && statusEvent.getStatus().getState() == TaskState.COMPLETED) {
                            // 获取最终响应文本
                            String responseText = extractTextFromStatusEvent(statusEvent);
                            if (responseText != null && !responseText.trim().isEmpty()) {
                                responseRef.set(responseText);
                            } else {
                                responseRef.set("数据分析任务已完成");
                            }
                            latch.countDown();
                        }
                    } else if (response.getResult() instanceof TaskArtifactUpdateEvent) {
                        // 处理制品更新
                        TaskArtifactUpdateEvent artifactEvent = (TaskArtifactUpdateEvent) response.getResult();
                        String artifactInfo = "数据处理结果: " + artifactEvent.getArtifact().getName();
                        responseRef.set(artifactInfo);
                        latch.countDown();
                    } else {
                        // 其他事件继续等待最终事件
                        log.info("Received intermediate event: {}", response.getResult().getClass().getSimpleName());
                    }
                } catch (Exception e) {
                    log.error("Error processing A2A response", e);
                    errorRef.set("A2A响应处理错误: " + e.getMessage());
                    latch.countDown();
                }
            },
            error -> {
                log.error("A2A stream error", error);
                errorRef.set("A2A通信错误: " + error.getMessage());
                latch.countDown();
            }
        );
        
        try {
            // 等待最多30秒
            boolean completed = latch.await(1200, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                return "A2A调用超时，请重试";
            }
            
            if (errorRef.get() != null) {
                return errorRef.get();
            }
            
            String response = responseRef.get();
            return response != null ? response : "A2A任务已完成";
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "A2A调用被中断: " + e.getMessage();
        }
    }
    
    private String extractTextFromStatusEvent(TaskStatusUpdateEvent statusEvent) {
        // 尝试从TaskStatus的message字段中获取文本
        if (statusEvent.getStatus() != null && statusEvent.getStatus().getMessage() != null) {
            com.zhouruojun.a2acore.spec.Message message = statusEvent.getStatus().getMessage();
            // 从Message中提取文本内容
            if (message.getParts() != null && !message.getParts().isEmpty()) {
                for (com.zhouruojun.a2acore.spec.Part part : message.getParts()) {
                    if (part instanceof com.zhouruojun.a2acore.spec.TextPart) {
                        return ((com.zhouruojun.a2acore.spec.TextPart) part).getText();
                    }
                }
            }
        }
        return null;
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

    public boolean checkAgentHealth(String agentName) {
        try {
            A2AClient client = getA2aClient(agentName);
            // 这里可以添加健康检查逻辑
            return client != null;
        } catch (Exception e) {
            log.warn("Health check failed for agent {}: {}", agentName, e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查Agent注册状态 - 用于调试
     */
    public Map<String, Object> getAgentRegistrationStatus(String agentName) {
        Map<String, Object> status = new HashMap<>();
        status.put("agentName", agentName);
        status.put("registeredInAgentRegistry", agentRegistry.containsKey(agentName));
        status.put("agentRegistryUrl", agentRegistry.get(agentName));
        
        List<A2AClient> clients = clientsMap.get(agentName);
        status.put("hasClients", !CollectionUtil.isEmpty(clients));
        status.put("clientCount", clients != null ? clients.size() : 0);
        
        if (!CollectionUtil.isEmpty(clients)) {
            A2AClient firstClient = clients.get(0);
            status.put("agentCardName", firstClient.getAgentCard().getName());
            status.put("agentCardUrl", firstClient.getAgentCard().getUrl());
            status.put("agentCardDescription", firstClient.getAgentCard().getDescription());
        }
        
        status.put("totalRegisteredAgents", clientsMap.size());
        status.put("totalClients", clients != null ? clients.size() : 0);
        
        return status;
    }

    public String getAgentInfo(String agentName) {
        try {
            A2AClient client = getA2aClient(agentName);
            AgentCard card = client.getAgentCard();
            return String.format("Name: %s, Version: %s, Description: %s", 
                card.getName(), card.getVersion(), card.getDescription());
        } catch (Exception e) {
            return "Agent info unavailable: " + e.getMessage();
        }
    }
}
