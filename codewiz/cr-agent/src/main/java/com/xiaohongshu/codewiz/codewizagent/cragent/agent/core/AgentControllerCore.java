package com.xiaohongshu.codewiz.codewizagent.cragent.agent.core;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.a2acore.spec.DataPart;
import com.xiaohongshu.codewiz.a2acore.spec.FileContent;
import com.xiaohongshu.codewiz.a2acore.spec.FilePart;
import com.xiaohongshu.codewiz.a2acore.spec.Message;
import com.xiaohongshu.codewiz.a2acore.spec.Part;
import com.xiaohongshu.codewiz.a2acore.spec.TextPart;
import com.xiaohongshu.codewiz.codewizagent.cragent.a2a.CrAgentTaskManager;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.AgentChatRequest;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.AgentOperateRequest;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.OperateTypeEnum;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.builder.AgentGraphBuilder;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.state.AgentMessageState;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.McpClientManager;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.ToolProviderManager;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local.LocalAgentReplyMessage;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local.LocalExecuteToolMessageResponse;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local.LocalToolMessageResponse;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local.ToolInterceptorRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.TextFileContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.text.TextFile;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
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

//    @Resource
//    private SessionManager sessionManager;

    @Resource
    private ToolProviderManager toolProviderManager;

    @Resource
    private McpClientManager mcpClientManager;

    @Resource
    private BaseCheckpointSaver saver;

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

        compiledGraphCache = new ConcurrentHashMap<>();
        stateGraphCache = new ConcurrentHashMap<>();
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
            sendMessage(request.getRequestId(), messages);
        });
    }

    public void processStreamWithA2a(String requestId,
                                     String sessionId,
                                     Message message,
                                     List<ToolSpecification> userTools,
                                     String userEmail) {
        CompletableFuture.runAsync(() -> {
            AsyncGenerator<NodeOutput<AgentMessageState>> messages = null;
            try {
                messages = processStream(requestId, sessionId, message, userTools, userEmail);
            } catch (Exception e) {
                log.error("processStreamWithWs error", e);
                throw new RuntimeException(e);
            }
            sendMessage(requestId, messages);
        });
    }

    public AsyncGenerator<NodeOutput<AgentMessageState>> processStream(String requestId,
                                                                       String sessionId,
                                                                       Message message,
                                                                       List<ToolSpecification> userTools,
                                                                       String userEmail) {
        AsyncGenerator<NodeOutput<AgentMessageState>> messages = null;
        try {
            Map<String, Object> messageMetadata = message.getMetadata();
            Object skillObj = messageMetadata.get("skill");
            String skill = null;
            if (skillObj != null) {
                skill = skillObj.toString();
            }
            StateGraph<AgentMessageState> stateGraph = stateGraphCache.getOrDefault(requestId,
                    buildGraphWithSkill(userTools, userEmail, requestId, skill));
            stateGraphCache.put(requestId, stateGraph);

            CompiledGraph<AgentMessageState> graph = compiledGraphCache.getOrDefault(requestId,
                    stateGraph.compile(buildCompileConfig()));
            compiledGraphCache.put(requestId, graph);

            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .threadId(requestId)
                    .build();

            List<Part> parts = message.getParts();
            List<Content> contents = parts.stream().map(part -> {
                if (part instanceof FilePart filePart) {
                    FileContent file = filePart.getFile();
                    TextFile base64data = TextFile.builder()
                            .url(file.getUri())
                            .base64Data(ValidationUtils.ensureNotBlank(file.getBytes(), "base64data"))
                            .mimeType(file.getMimeType())
                            .build();
                    return TextFileContent.from(base64data);
                } else if (part instanceof DataPart dataPart) {
                    return TextContent.from(JSONObject.toJSONString(dataPart.getData()));
                } else if (part instanceof TextPart textPart) {
                    return TextContent.from(textPart.getText());
                } else {
                    return TextContent.from(JSONObject.toJSONString(part.getMetadata()));
                }
            }).collect(Collectors.toList());
            return graph.stream(Map.of(
                    "messages", UserMessage.from(contents),
                    "sessionId", sessionId,
                    "requestId", requestId,
                    "username", userEmail
                    ), runnableConfig);
        } catch (Exception e) {
            log.error("processStreamWithA2a error", e);
            throw new RuntimeException(e);
        }
    }

    private AsyncGenerator<NodeOutput<AgentMessageState>> processStream(AgentChatRequest request, List<ToolSpecification> userTools, String userEmail)
            throws GraphStateException {

        StateGraph<AgentMessageState> stateGraph = stateGraphCache.getOrDefault(request.getRequestId(),
                buildGraph(userTools, userEmail, request.getRequestId()));
        stateGraphCache.put(request.getRequestId(), stateGraph);

        CompiledGraph<AgentMessageState> graph = compiledGraphCache.getOrDefault(request.getRequestId(),
                stateGraph.compile(buildCompileConfig()));
        compiledGraphCache.put(request.getRequestId(), graph);
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
                .threadId(request.getRequestId())
                .build();
        return graph.stream(Map.of("messages", UserMessage.from(text)), runnableConfig);
    }

    private StateGraph<AgentMessageState> buildGraphWithSkill(List<ToolSpecification> userTools, String username, String requestId, String skill)
            throws GraphStateException {
        toolProviderManager.loadToolProvider(userTools, username, requestId);
        StateGraph<AgentMessageState> stateGraph = new AgentGraphBuilder()
                .chatLanguageModel(openAiChatModel)
//                .chatLanguageModel(openAiStreamingChatModel)
                .toolProviderManager(toolProviderManager)
                .skill(skill)
                .username(username)
                .requestId(requestId)
                .build();
        return stateGraph;
    }

    public StateGraph<AgentMessageState> buildGraph(List<ToolSpecification> userTools, String username, String requestId)
            throws GraphStateException {
        toolProviderManager.loadToolProvider(userTools, username, requestId);
        StateGraph<AgentMessageState> stateGraph = new AgentGraphBuilder()
                .chatLanguageModel(openAiChatModel)
//                .chatLanguageModel(openAiStreamingChatModel)
                .toolProviderManager(toolProviderManager)
                .username(username)
                .requestId(requestId)
                .build();
        return stateGraph;
    }

    private CompileConfig buildCompileConfig() {
        return CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptBefore("actionWaiting", "humanConfirm", "humanPostConfirm")
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
                String requestId = localToolMessageResponse.getRequestId();
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(requestId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(requestId);
                StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);
                LocalExecuteToolMessageResponse localExecuteToolMessageResponse = tools.get(0);
                ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(localExecuteToolMessageResponse.getId(),
                        localExecuteToolMessageResponse.getToolName(), localExecuteToolMessageResponse.getText());

                Map<String, Object> messages1 = Map.of("messages", toolExecutionResultMessage);
                RunnableConfig newConfig = graph.updateState(state.getConfig(), messages1);
                messages = graph.stream(null, newConfig);
            } catch (Exception e) {
                log.error("resumeStreamWithTools error", e);
                throw new RuntimeException(e);
            }
            sendMessage(localToolMessageResponse.getRequestId(), messages);
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

                Map<String, Object> messages1 = Map.of("messages",
                        UserMessage.from(toolInterceptorRequest.getName(), JSONObject.toJSONString(toolInterceptorRequest)));
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
            String requestId = toolInterceptorRequest.getRequestId();
            try {
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(requestId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(requestId);
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
            sendMessage(requestId, messages);
        });
    }

    public void humanInput(AgentChatRequest request) {
        CompletableFuture.runAsync(() -> {
            AsyncGenerator<NodeOutput<AgentMessageState>> messages = null;
            String requestId = request.getRequestId();
            try {
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(requestId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(requestId);
                StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);

                Map<String, Object> messages1 = Map.of("messages", UserMessage.from(request.getChat()));
                RunnableConfig newConfig = graph.updateState(state.getConfig(), messages1);
                messages = graph.stream(null, newConfig);
            } catch (Exception e) {
                log.error("resumeStreamWithTools error", e);
                throw new RuntimeException(e);
            }
            sendMessage(requestId, messages);
        });
    }

    private void sendMessage(String requestId, AsyncGenerator<NodeOutput<AgentMessageState>> messages) {
        if (CollectionUtil.isEmpty(messages)) {
            return;
        }
        try {
            for (NodeOutput<AgentMessageState> message : messages) {
                CrAgentTaskManager.sendTextEvent(requestId, message.state(), message.node());
            }
        } catch (Exception e) {
            log.error("processStreamWithWs message error", e);
            throw new RuntimeException(e);
        }
    }

    public void viewState(String requestId) {
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(requestId)
                .build();
        CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(requestId);
        StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);
    }

    public StateGraph<AgentMessageState> getGraph(String requestId) {
        return stateGraphCache.get(requestId);
    }
}
