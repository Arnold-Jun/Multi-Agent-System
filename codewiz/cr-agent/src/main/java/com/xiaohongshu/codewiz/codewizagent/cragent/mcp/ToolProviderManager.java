package com.xiaohongshu.codewiz.codewizagent.cragent.mcp;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local.LocalServerToolProvider;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 管理mcp与local tools
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/9 14:32
 */
@Slf4j
@Component
public class ToolProviderManager {

    @Resource
    private McpClientManager mcpClientManager;

    private Map<String, List<ToolProvider>> toolProviderMap = new ConcurrentHashMap<>();

    private Map<String, List<ToolProviderResult>> toolProviderResultMap = new ConcurrentHashMap<>();

    public List<ToolSpecification> toolSpecifications(String username, String requestId) {
        String toolProviderKey = getToolProviderKey(username, requestId);
        List<ToolProvider> toolProviders = toolProviderMap.get(toolProviderKey);
        if (CollectionUtil.isEmpty(toolProviders)) {
            toolProviders = loadToolProvider(null, username, requestId);
        }
        List<ToolProviderResult> toolProviderResultList =
                toolProviders.stream().map(toolProvider -> toolProvider.provideTools(null)).collect(Collectors.toList());
        toolProviderResultMap.put(toolProviderKey, toolProviderResultList);
        return toolProviderResultList.stream()
                .flatMap(toolProviderResult -> toolProviderResult.tools().keySet().stream())
                .collect(Collectors.toList());
    }

    public Optional<ToolExecutionResultMessage> execute(@NonNull ToolExecutionRequest request, Object sessionId, String username, String requestId) {
        if (request == null) {
            throw new NullPointerException("request is marked non-null but is null");
        } else {
            String toolProviderKey = getToolProviderKey(username, requestId);
            log.info("execute: {}, toolProviderKey:{}", request.name(), toolProviderKey);
            List<ToolProviderResult> toolProviderResults = toolProviderResultMap.getOrDefault(toolProviderKey, new ArrayList<>());
            Optional<ToolExecutionResultMessage> result =
                    toolProviderResults.stream().flatMap(toolProviderResult -> toolProviderResult.tools().entrySet().stream())
                            .filter(entry -> request.name().equals(entry.getKey().name()))
                            .findFirst().map((e) -> {
                                String value = e.getValue().execute(request, sessionId);
                                return new ToolExecutionResultMessage(request.id(), request.name(), value);
                            });
            return result;
        }
    }

    public Optional<ToolExecutionResultMessage> execute(@NonNull Collection<ToolExecutionRequest> requests, Object sessionId, String username, String requestId) {
        if (requests == null) {
            throw new NullPointerException("requests is marked non-null but is null");
        } else {
            for(ToolExecutionRequest request : requests) {
                Optional<ToolExecutionResultMessage> result = this.execute(request, sessionId, username, requestId);
                if (result.isPresent()) {
                    return result;
                }
            }

            return Optional.empty();
        }
    }

    public Optional<ToolExecutionResultMessage> execute(ToolExecutionRequest request, String username, String requestId) {
        return this.execute(request, (Object) null, username, requestId);
    }

    public Optional<ToolExecutionResultMessage> execute(Collection<ToolExecutionRequest> requests) {
        return this.execute((Collection)requests, (Object)null, null, null);
    }

    /**
     * 在用户第一次请求是使用，加载mcp的工具与用户自己的工具
     * @param userTools
     * @return
     */
    public List<ToolProvider> loadToolProvider(List<ToolSpecification> userTools, String username, String requestId) {
        String toolProviderKey = getToolProviderKey(username, requestId);
        List<ToolProvider> oldToolProviders = toolProviderMap.get(toolProviderKey);
        if (CollectionUtil.isNotEmpty(oldToolProviders)) {
            log.info("loadToolProvider clear tool provider for init new request: username:{}, requestId:{}, userTools:{}", username, requestId, JSONObject.toJSON(userTools));
        }
        List<ToolProvider> toolProviders = new ArrayList<>(mcpClientManager.getToolProviderMap().values());
        LocalServerToolProvider localServerToolProvider = LocalServerToolProvider.builder()
                .toolSpecifications(userTools)
                .username(username)
                .requestId(requestId)
                .failIfOneServerFails(false)
                .build();
        toolProviders.add(localServerToolProvider);
        toolProviderMap.put(toolProviderKey, toolProviders);
        return toolProviders;
    }

    public String getToolProviderKey(String username, String requestId) {
        return username + "_" + requestId;
    }
}
