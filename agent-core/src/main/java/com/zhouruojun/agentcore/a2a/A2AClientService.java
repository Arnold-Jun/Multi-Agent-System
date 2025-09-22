package com.zhouruojun.agentcore.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouruojun.agentcore.config.AgentConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A2A客户端服务
 * 用于与其他智能体进行通信
 */
@Service
@Slf4j
public class A2AClientService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public A2AClientService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(AgentConstants.CONNECT_TIMEOUT_SECONDS).toMillis());
        factory.setReadTimeout((int) Duration.ofMinutes(AgentConstants.READ_TIMEOUT_MINUTES).toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 同步调用其他智能体
     */
    public A2AInvocationResult invokeAgent(String agentName,
                                           String taskType,
                                           Map<String, Object> parameters,
                                           String sessionId,
                                           String agentUrl) {
        if (agentUrl == null) {
            return A2AInvocationResult.failure("发送端可能未注册登录，agent URL 为空", HttpStatus.NOT_FOUND.value());
        }

        String endpoint = agentUrl + "/a2a/invoke";
        Map<String, Object> request = new HashMap<>();
        request.put("taskType", taskType);
        request.put("parameters", parameters);
        request.put("sessionId", sessionId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            log.info("Invoking agent {} at {} with task {}", agentName, endpoint, taskType);
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                return A2AInvocationResult.failure(
                        "HTTP " + response.getStatusCode().value() + ": " + response.getStatusCode(),
                        response.getStatusCode().value());
            }

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                return A2AInvocationResult.failure("返回内容为空", response.getStatusCode().value());
            }

            Object status = responseBody.get("status");
            if ("completed".equals(status)) {
                String result = (String) responseBody.getOrDefault("result", "");
                return A2AInvocationResult.success(result, response.getStatusCode().value());
            }

            String error = (String) responseBody.getOrDefault("error", "未提供异常信息");
            return A2AInvocationResult.failure(error, response.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            log.error("Timeout invoking agent {} at {}", agentName, endpoint, ex);
            return A2AInvocationResult.failure("请检查地址或者时间中进行连接超时", HttpStatus.GATEWAY_TIMEOUT.value());
        } catch (RestClientException ex) {
            log.error("Rest client error invoking agent {}", agentName, ex);
            return A2AInvocationResult.failure("调用单位连接失败: " + ex.getMessage(), HttpStatus.BAD_GATEWAY.value());
        } catch (Exception ex) {
            log.error("Unexpected error invoking agent {}", agentName, ex);
            return A2AInvocationResult.failure("未知异常: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * 异步调用其他智能体
     */
    public CompletableFuture<String> invokeAgentAsync(String agentName,
                                                     String taskType,
                                                     Map<String, Object> parameters,
                                                     String sessionId,
                                                     String agentUrl) {
        return CompletableFuture.supplyAsync(() -> {
            A2AInvocationResult result = invokeAgent(agentName, taskType, parameters, sessionId, agentUrl);
            return result.success() ? result.response() : "Error: " + result.error();
        });
    }

    /**
     * 获取智能体信息
     */
    public String getAgentInfo(String agentName, String agentUrl) {
        try {
            if (agentUrl == null) {
                return "Error: 未找到智能体 " + agentName;
            }

            String endpoint = agentUrl + "/a2a/agent-info";
            ResponseEntity<Map> response = restTemplate.getForEntity(endpoint, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.writeValueAsString(response.getBody());
            } else {
                return "Error: 获取智能体信息失败";
            }
        } catch (Exception e) {
            log.error("Error getting agent info: {}", agentName, e);
            return "Error: 获取智能体信息失败- " + e.getMessage();
        }
    }

    /**
     * 检查智能体健康状态
     */
    public boolean checkAgentHealth(String agentName, String agentUrl) {
        try {
            if (agentUrl == null) {
                log.warn("Agent URL is null for: {}", agentName);
                return false;
            }

            String endpoint = agentUrl + "/a2a/health";
            log.info("Checking health for agent {} at endpoint: {}", agentName, endpoint);

            ResponseEntity<Map> response = restTemplate.getForEntity(endpoint, Map.class);
            boolean isHealthy = response.getStatusCode() == HttpStatus.OK;

            log.info("Health check result for {}: status={}, healthy={}, responseBody={}",
                    agentName, response.getStatusCode(), isHealthy, response.getBody());

            return isHealthy;
        } catch (Exception e) {
            log.warn("Health check failed for agent: {} at URL: {}", agentName, agentUrl, e);
            return false;
        }
    }

    private String pollTaskResult(String agentUrl, String taskId) {
        try {
            String statusEndpoint = agentUrl + "/a2a/task-status/" + taskId;
            int maxRetries = 120; // 最多等待120次，每次1秒，总共2分钟

            for (int i = 0; i < maxRetries; i++) {
                Thread.sleep(1000);
                ResponseEntity<Map> response = restTemplate.getForEntity(statusEndpoint, Map.class);
                if (response.getStatusCode() == HttpStatus.OK) {
                    Map<String, Object> body = response.getBody();
                    if (body == null) continue;
                    String status = (String) body.get("status");

                    if ("completed".equals(status)) {
                        return (String) body.get("result");
                    } else if ("error".equals(status)) {
                        return "Error: " + body.get("error");
                    }
                }
            }

            return "Error: 任务超时，未能预期时间完成";
        } catch (Exception e) {
            log.error("Error polling task result", e);
            return "Error: 轮询任务结果失败 - " + e.getMessage();
        }
    }
}