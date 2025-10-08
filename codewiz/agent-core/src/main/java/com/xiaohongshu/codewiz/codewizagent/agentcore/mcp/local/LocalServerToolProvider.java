package com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local;

import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.SessionManager;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户本地服务工具提供者
 * 该provider为运行时构建
 */
@Slf4j
public class LocalServerToolProvider implements ToolProvider {

    private final boolean failIfOneServerFails;

    private String channelId;

    private String requestId;

    private List<ToolSpecification> userLocalTools;

    private LocalServerToolProvider(Builder builder) {
        this.failIfOneServerFails = Utils.getOrDefault(builder.failIfOneServerFails, false);
        this.userLocalTools = Utils.getOrDefault(builder.toolSpecifications, new ArrayList<>());
        this.channelId = Utils.getOrDefault(builder.username, "");
        this.requestId = Utils.getOrDefault(builder.requestId, "");
    }

    @Override
    public ToolProviderResult provideTools(final ToolProviderRequest request) {
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        try {
            for (ToolSpecification toolSpecification : userLocalTools) {
                builder.add(
                        toolSpecification, (executionRequest, memoryId) ->
                                SessionManager.getInstance().sendNativeActionMessage(requestId,
                                    parseToolExecutionToJSON(executionRequest, requestId)));
            }
        } catch (Exception e) {
            if (failIfOneServerFails) {
                throw new RuntimeException("Failed to retrieve tools from MCP server", e);
            } else {
                log.warn("Failed to retrieve tools from MCP server", e);
            }
        }

        return builder.build();
    }

    private String parseToolExecutionToJSON(ToolExecutionRequest toolExecutionRequest, String requestId) {
        LocalExecuteToolMessageRequest localExecuteToolMessageRequest = new LocalExecuteToolMessageRequest();
        LocalToolMessageRequest localToolMessageRequest = new LocalToolMessageRequest();

        localExecuteToolMessageRequest.setId(toolExecutionRequest.id());
        localExecuteToolMessageRequest.setName(toolExecutionRequest.name());
        localExecuteToolMessageRequest.setArguments(toolExecutionRequest.arguments());

        // todo
//        localToolMessageRequest.setMemoryId();
//        localToolMessageRequest.setPreCheck();
//        localToolMessageRequest.setPostCheck();
        localToolMessageRequest.setRequestId(requestId);
        localToolMessageRequest.setTools(List.of(localExecuteToolMessageRequest));
        return JSONObject.toJSONString(localToolMessageRequest);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {


        private Boolean failIfOneServerFails;

        private List<ToolSpecification> toolSpecifications;

        private String username;

        private String requestId;

        /**
         * If this is true, then the tool provider will throw an exception if it fails to list tools from any of the servers.
         * If this is false (default), then the tool provider will ignore the error and continue with the next server.
         */
        public Builder failIfOneServerFails(boolean failIfOneServerFails) {
            this.failIfOneServerFails = failIfOneServerFails;
            return this;
        }

        /**
         * Insert local user tool specifications
         * @param toolSpecifications
         * @return
         */
        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        /**
         * channelId with username
         * @param username
         * @return
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * requestId
         * @param requestId
         * @return
         */
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public LocalServerToolProvider build() {
            return new LocalServerToolProvider(this);
        }
    }
}