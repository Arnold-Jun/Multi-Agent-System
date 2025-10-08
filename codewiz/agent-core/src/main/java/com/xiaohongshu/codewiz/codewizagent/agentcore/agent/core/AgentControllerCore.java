package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.core;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaohongshu.codewiz.a2a.client.autoconfiguration.A2aAgentProperties;
import com.xiaohongshu.codewiz.a2a.client.autoconfiguration.A2aAgentsProperties;
import com.xiaohongshu.codewiz.a2acore.spec.TaskArtifactUpdateEvent;
import com.xiaohongshu.codewiz.a2acore.spec.TaskSendParams;
import com.xiaohongshu.codewiz.a2acore.spec.TaskStatusUpdateEvent;
import com.xiaohongshu.codewiz.a2acore.spec.UpdateEvent;
import com.xiaohongshu.codewiz.codewizagent.agentcore.a2a.A2aClientManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.a2a.A2aTaskManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.a2a.A2aTaskUpdate;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.AgentChatRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.AgentOperateRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.OperateTypeEnum;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.builder.AgentGraphBuilder;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.state.AgentMessageState;
import com.xiaohongshu.codewiz.codewizagent.agentcore.common.ApplicationContextUtil;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.McpClientManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.ToolProviderManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalAgentReplyMessage;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalExecuteToolMessageResponse;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalToolMessageResponse;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.ToolInterceptorRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.scene.SceneAgentConfig;
import com.xiaohongshu.codewiz.codewizagent.agentcore.scene.SceneConfig;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.ObserverMessage;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.ObserverMessageState;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.SessionManager;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/29 03:06
 */
@Slf4j
@Component
public class AgentControllerCore {

    private Map<String, CompiledGraph<AgentMessageState>> compiledGraphCache;
    private Map<String, StateGraph<AgentMessageState>> stateGraphCache;

    private OpenAiChatModel openAiChatModel;
    private OpenAiStreamingChatModel openAiStreamingChatModel;


    @Resource
    private ToolProviderManager toolProviderManager;


    private BaseCheckpointSaver saver;

    private ObjectMapper objectMapper;

    private AtomicBoolean mcpInitRun = new AtomicBoolean(false);

    @PostConstruct
    public void init() throws GraphStateException {
        openAiChatModel = OpenAiChatModel.builder()
                .baseUrl("http://redservingapi.devops.xiaohongshu.com/v1")
                .apiKey("QSTaacc44c2c29fd5647e708d612836f32f")
                .modelName("qwen2.5-72b-funtion-tool")
//                .modelName("qwq-32b")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();
//        openAiStreamingChatModel = OpenAiStreamingChatModel.builder()
//                .baseUrl("http://redservingapi.devops.xiaohongshu.com/v1")
//                .apiKey("QSTaacc44c2c29fd5647e708d612836f32f")
//                .modelName("qwq-32b")
//                .temperature(0.0)
//                .logRequests(true)
//                .logResponses(true)
//                .build();
        log.info("AgentController init");

        saver = new MemorySaver();

        compiledGraphCache = new ConcurrentHashMap<>();
        stateGraphCache = new ConcurrentHashMap<>();

        objectMapper = new ObjectMapper();
    }

    public String processStreamReturnStr(AgentChatRequest request) throws GraphStateException {
        AsyncGenerator<NodeOutput<AgentMessageState>> messages = processStream(request, null, null);
        String result = "";
        for (NodeOutput<AgentMessageState> message : messages) {
            log.info("message: {}", message);
            result = JSONObject.toJSONString(message);
        }
        return result;
    }

    public void processStreamWithWs(AgentChatRequest request, List<ToolSpecification> userTools, String userEmail) {
        CompletableFuture.runAsync(() -> {
            AsyncGenerator<NodeOutput<AgentMessageState>> messages = null;
            try {
                messages = processStream(request, userTools, userEmail);
            } catch (Exception e) {
                log.error("processStreamWithWs error", e);
                throw new RuntimeException(e);
            }
            sendMessage(request.getSessionId(), messages);
        });
    }

    private AsyncGenerator<NodeOutput<AgentMessageState>> processStream(AgentChatRequest request, List<ToolSpecification> userTools, String userEmail)
            throws GraphStateException {

        // todo 配置暂时写死在这里。
        List<SceneAgentConfig> sceneAgentConfigs = ApplicationContextUtil.getBean(A2aClientManager.class)
                .getAgentCards().stream().map(agentCard -> {
            String agentName = agentCard.getName();
            SceneAgentConfig agentProperties = new SceneAgentConfig();
            agentProperties.setName(agentName);
            agentProperties.setBaseUrl(agentCard.getUrl());
            return agentProperties;
        }).toList();
        SceneConfig sceneConfig = new SceneConfig();
        sceneConfig.setSceneAgentConfigs(sceneAgentConfigs);

        StateGraph<AgentMessageState> stateGraph = stateGraphCache.getOrDefault(request.getSessionId(),
                buildGraph(userTools, userEmail, request.getSessionId(), sceneConfig));
        stateGraphCache.put(request.getSessionId(), stateGraph);

        CompiledGraph<AgentMessageState> graph = compiledGraphCache.getOrDefault(request.getSessionId(),
                stateGraph.compile(buildCompileConfig()));
        compiledGraphCache.put(request.getSessionId(), graph);
        String text = null;
        if (CollectionUtil.isNotEmpty(request.getOperates())) {
            for (AgentOperateRequest operate : request.getOperates()) {
                if (OperateTypeEnum.CR.equals(operate.getOperateType())) {
                    text = CollectionUtil.isEmpty(operate.getText()) ? "/mr create" : operate.getText().get(0);
                }
            }
        }
        if (StringUtils.isEmpty(text)) {
            text = request.getChat();
        }
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(request.getSessionId())
                .build();
        return graph.stream(Map.of("messages", UserMessage.from(text), "sessionId", request.getSessionId()), runnableConfig);
    }

    public StateGraph<AgentMessageState> buildGraph(List<ToolSpecification> userTools, String username, String requestId, SceneConfig sceneConfig)
            throws GraphStateException {
        toolProviderManager.loadToolProvider(userTools, username, requestId);

        StateGraph<AgentMessageState> stateGraph = new AgentGraphBuilder()
                .chatLanguageModel(openAiChatModel)
//                .chatLanguageModel(openAiStreamingChatModel)
                .toolProviderManager(toolProviderManager)
                .username(username)
                .requestId(requestId)
                .a2aClientManager(ApplicationContextUtil.getBean(A2aClientManager.class))
                .sceneConfig(sceneConfig)
                .build();
        return stateGraph;
    }

    private CompileConfig buildCompileConfig() {
        return CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptBefore("agentInvokeStateCheck")
                .interruptAfter("userInput")
                .build();
    }

    public void getSaver(String requestId) {
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(requestId)
                .build();
        Optional<Checkpoint> checkpoint = saver.get(runnableConfig);
        Collection<Checkpoint> list = saver.list(runnableConfig);
        log.info("checkpoint: {}", checkpoint.get());
        log.info("list: {}", list);
    }

    public void resumeStreamWithTools(LocalAgentReplyMessage localAgentReplyMessage, String username) {
        LocalToolMessageResponse localToolMessageResponse = localAgentReplyMessage.getResponse();
        if (localToolMessageResponse == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            AsyncGenerator<NodeOutput<AgentMessageState>> messages = null;
            try {
                List<LocalExecuteToolMessageResponse> tools = localToolMessageResponse.getTools();
                if (CollectionUtil.isEmpty(tools)) {
                    log.info("resumeStream stop, tools is error: localToolMessageResponse {}", localToolMessageResponse);
                    return;
                }
                String memoryId = localToolMessageResponse.getMemoryId();
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(memoryId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(memoryId);
                StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);
                LocalExecuteToolMessageResponse localExecuteToolMessageResponse = tools.get(0);
                ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(localExecuteToolMessageResponse.getId(),
                        localExecuteToolMessageResponse.getToolName(), localExecuteToolMessageResponse.getText());

//                LocalToolMessageRequest localToolMessageRequest = localAgentReplyMessage.getRequest();
//                Map<String, Object> itemMessage;
//                if (localToolMessageRequest != null && CollectionUtil.isNotEmpty(localToolMessageRequest.getTools())) {
//                    LocalExecuteToolMessageRequest localExecuteToolMessageRequest = localToolMessageRequest.getTools().getFirst();
//                    ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
//                            .id(localExecuteToolMessageRequest.getId())
//                            .arguments(localExecuteToolMessageRequest.getArguments())
//                            .name(localExecuteToolMessageRequest.getName())
//                            .build();
//                    itemMessage = Map.of("toolRequest", toolExecutionRequest, "toolResponse", toolExecutionResultMessage);
//                } else {
//                    itemMessage = Map.of("toolResponse", toolExecutionResultMessage);
//                }
                Map<String, Object> messages1 = Map.of("messages", toolExecutionResultMessage, "method", "tool_call");
                RunnableConfig newConfig = graph.updateState(state.getConfig(), messages1);
                messages = graph.stream(null, newConfig);
            } catch (Exception e) {
                log.error("resumeStreamWithTools error", e);
                throw new RuntimeException(e);
            }
            sendMessage(localToolMessageResponse.getMemoryId(), messages);
        });
    }

    public void humanToolsStream(ToolInterceptorRequest toolInterceptorRequest, String method) {
        CompletableFuture.runAsync(() -> {
            AsyncGenerator<NodeOutput<AgentMessageState>> messages = null;
            String sessionId = toolInterceptorRequest.getSessionId();
            try {
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(sessionId);
                StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);

                Map<String, Object> messages1 = Map.of(
                        "messages", UserMessage.from(toolInterceptorRequest.getName(), objectMapper.writeValueAsString(toolInterceptorRequest)),
                        "method", method);
                RunnableConfig newConfig = graph.updateState(state.getConfig(), messages1);
                messages = graph.stream(null, newConfig);
            } catch (Exception e) {
                log.error("resumeStreamWithTools error", e);
                throw new RuntimeException(e);
            }
            sendMessage(sessionId, messages);
        });
    }

    public void humanConfirm(ToolInterceptorRequest toolInterceptorRequest) {
        CompletableFuture.runAsync(() -> {
            AsyncGenerator<NodeOutput<AgentMessageState>> messages = null;
            String requestId = toolInterceptorRequest.getRequestId();
            try {
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(requestId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(requestId);
                StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);

                Map<String, Object> messages1 = Map.of(
                        "messages", UserMessage.from(toolInterceptorRequest.getName(), objectMapper.writeValueAsString(toolInterceptorRequest)),
                        "method", "human_confirm");
                RunnableConfig newConfig = graph.updateState(state.getConfig(), messages1);
                messages = graph.stream(null, newConfig);
            } catch (Exception e) {
                log.error("resumeStreamWithTools error", e);
                throw new RuntimeException(e);
            }
            sendMessage(requestId, messages);
        });
    }

    public void humanReplay(ToolInterceptorRequest toolInterceptorRequest) {
        CompletableFuture.runAsync(() -> {
            AsyncGenerator<NodeOutput<AgentMessageState>> messages = null;
            String sessionId = toolInterceptorRequest.getSessionId();
            try {
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(sessionId);
                StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);
                Collection<StateSnapshot<AgentMessageState>> stateHistory = graph.getStateHistory(runnableConfig);
                StateSnapshot<AgentMessageState> replayState = null;
                for (StateSnapshot<AgentMessageState> snapshot : stateHistory) {
                    if (snapshot.state().messages().size() == state.state().messages().size() - 1) {
                        replayState = snapshot;
                    }
                }
                if (replayState == null) {
                    log.error("humanReplay error, replayState is null");
                }
                messages = graph.stream(null, replayState.config());
            } catch (Exception e) {
                log.error("resumeStreamWithTools error", e);
                throw new RuntimeException(e);
            }
            sendMessage(sessionId, messages);
        });
    }

    public void humanInput(AgentChatRequest request) {
        CompletableFuture.runAsync(() -> {
            AsyncGenerator<NodeOutput<AgentMessageState>> messages = null;
            String sessionId = request.getSessionId();
            try {
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(sessionId);
                StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);

                Map<String, Object> messages1 = Map.of(
                        "messages", UserMessage.from(request.getChat()),
                        "method", "human_input"
                );
                RunnableConfig newConfig = graph.updateState(state.getConfig(), messages1);
                messages = graph.stream(null, newConfig);
            } catch (Exception e) {
                log.error("resumeStreamWithTools error", e);
                throw new RuntimeException(e);
            }
            sendMessage(sessionId, messages);
        });
    }

    public void a2aAiMessageOnEvent(UpdateEvent updateEvent) {
        TaskSendParams taskParams = A2aTaskManager.getInstance().getTaskParams(updateEvent.getId());
        String sessionId = taskParams.getSessionId();
        A2aTaskUpdate a2aTaskUpdate = new A2aTaskUpdate();
        a2aTaskUpdate.setTaskId(taskParams.getId());
        a2aTaskUpdate.setSessionId(sessionId);
        if (updateEvent instanceof TaskStatusUpdateEvent taskStatusUpdateEvent) {
            log.info("onEvent receive task status update: {}", taskStatusUpdateEvent);
            // 任务状态变化，可能是失败、成功、取消等。对当前流程进行下一步处理
            a2aTaskUpdate.setUpdateType("status");
            a2aTaskUpdate.setUpdateEvent(taskStatusUpdateEvent);
        } else if (updateEvent instanceof TaskArtifactUpdateEvent taskArtifactUpdateEvent) {
            // 纯制品的状态变化，与交互无关了。
            log.info("onEvent receive artifact update: {}", JSONObject.toJSONString(taskArtifactUpdateEvent));
            a2aTaskUpdate.setUpdateType("artifact");
            a2aTaskUpdate.setUpdateEvent(taskArtifactUpdateEvent);
        } else {
            log.error("receive task streaming response error, result:{}", JSONObject.toJSONString(updateEvent));
            throw new RuntimeException("receive task streaming response error" + JSONObject.toJSONString(updateEvent));
        }

        CompletableFuture.runAsync(() -> {
            try {
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(sessionId);
                StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);

                Map<String, Object> messages1 = Map.of(
                        "messages", AiMessage.from(objectMapper.writeValueAsString(a2aTaskUpdate))
                );
                RunnableConfig newConfig = graph.updateState(state.getConfig(), messages1);
                AsyncGenerator<NodeOutput<AgentMessageState>> stream = graph.stream(null, newConfig);
                try {
                    for (NodeOutput<AgentMessageState> agentMessageStateNodeOutput : stream) {
                        ObserverMessage observerMessage = new ObserverMessage();
                        observerMessage.setNode(agentMessageStateNodeOutput.node());
                        observerMessage.setSubGraph(agentMessageStateNodeOutput.isSubGraph());
                        ObserverMessageState observerMessageState = new ObserverMessageState();
                        observerMessageState.of(agentMessageStateNodeOutput.state().data());
                        observerMessage.setState(observerMessageState);
                        String messageStr = JSONObject.toJSONString(observerMessage);
                        log.info("onEvent receive agentMessageState: {}", agentMessageStateNodeOutput);
                    }
                } catch (Exception e) {
                    log.error("resumeStreamWithTools error", e);
                }
            } catch (Exception e) {
                log.error("resumeStreamWithTools error", e);
                throw new RuntimeException(e);
            }
        });

    }


    private void sendMessage(String sessionId, AsyncGenerator<NodeOutput<AgentMessageState>> messages) {
        if (CollectionUtil.isEmpty(messages)) {
            return;
        }
        try {
            for (NodeOutput<AgentMessageState> message : messages) {
                ObserverMessage observerMessage = new ObserverMessage();
                observerMessage.setNode(message.node());
                observerMessage.setSubGraph(message.isSubGraph());
                ObserverMessageState observerMessageState = new ObserverMessageState();
                observerMessageState.of(message.state().data());
                observerMessage.setState(observerMessageState);
                String messageStr = JSONObject.toJSONString(observerMessage);
                log.info("message: {}", messageStr);
//                sessionManager.sendObserverMessage(requestId, messageStr);
//                sessionManager.sendNativeObserverMessage(requestId, messageStr);
                SessionManager.getInstance().sendNativeObserverMessage(sessionId, messageStr);
            }
        } catch (Exception e) {
            log.error("processStreamWithWs message error", e);
            throw new RuntimeException(e);
        }
    }

    public void viewState(String sessionId) {
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(sessionId)
                .build();
        CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(sessionId);
        StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);
    }

    public StateGraph<AgentMessageState> getGraph(String sessionId) {
        return stateGraphCache.get(sessionId);
    }
}
