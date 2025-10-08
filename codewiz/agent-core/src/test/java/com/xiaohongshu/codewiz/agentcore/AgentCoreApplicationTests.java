package com.xiaohongshu.codewiz.agentcore;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.a2acore.spec.DataPart;
import com.xiaohongshu.codewiz.a2acore.spec.Message;
import com.xiaohongshu.codewiz.a2acore.spec.Role;
import com.xiaohongshu.codewiz.a2acore.spec.TaskSendParams;
import com.xiaohongshu.codewiz.a2acore.spec.TextPart;
import com.xiaohongshu.codewiz.a2acore.spec.message.util.Uuid;
import com.xiaohongshu.codewiz.codewizagent.agentcore.a2a.A2aTaskManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.builder.AgentGraphBuilder;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.serializers.AgentSerializers;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.state.AgentMessageState;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.ToolProviderManager;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.NodeAction;
import org.junit.Before;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.annotation.BeforeTestClass;

@Slf4j
class AgentCoreApplicationTests {

    static OpenAiChatModel openAiChatModel;


    @BeforeAll
    public static void before() {
        openAiChatModel = OpenAiChatModel.builder()
                .baseUrl("http://redservingapi.devops.xiaohongshu.com/v1")
                .apiKey("QSTaacc44c2c29fd5647e708d612836f32f")
                .modelName("qwen2.5-72b-funtion-tool")
//                .modelName("qwq-32b")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void TestGraphWithMessageState() throws GraphStateException {

        String sessionId = Uuid.uuid4hex();

        StateGraph<AgentMessageState> graph =
                new StateGraph<>(AgentMessageState.SCHEMA, AgentSerializers.STD.object())
                        .addNode("A", AsyncNodeAction.node_async(
                                agentMessageState -> {
                                    log.info("A method:{}", agentMessageState.data().getOrDefault("method", "default"));
                                    TaskSendParams taskSendParams =
                                            A2aTaskManager.getInstance().lastTaskParams(agentMessageState.sessionId().orElse("-1"));
                                    if (taskSendParams == null) {
                                        String userMessage = "默认发起CR";
                                        if (agentMessageState.lastMessage().isPresent()) {
                                            userMessage = ((UserMessage)agentMessageState.lastMessage().get()).singleText();
                                        }
                                        taskSendParams = new TaskSendParams();
                                        taskSendParams.setId(Uuid.uuid4hex());
                                        taskSendParams.setSessionId(sessionId);
                                        taskSendParams.setAcceptedOutputModes(List.of("text", "data"));
                                        DataPart dataPart = new DataPart();
                                        dataPart.setData(Map.of("k1", "v1"));
                                        Message build = Message.builder()
                                                .parts(List.of(new TextPart(userMessage), dataPart))
                                                .metadata(Map.of("method", "chat"))
                                                .role(Role.USER)
                                                .build();
                                        taskSendParams.setMessage(build);
                                        A2aTaskManager.getInstance().saveTaskParams(taskSendParams);
                                        log.info("A first subscribe taskSendParams: {}", JSONObject.toJSONString(taskSendParams));
                                        return Map.of(
                                                "next", "B",
                                                "requestId", sessionId);
                                    } else {
                                        log.info("A second subscribe taskSendParams: {}", JSONObject.toJSONString(taskSendParams));
                                        A2aTaskManager.getInstance().cleanTaskBySession(agentMessageState.sessionId().get());
                                        log.info("A remove subscribe taskSendParams");
                                        return Map.of(
                                                "next", "B",
                                                "requestId", sessionId);
                                    }
                                }))
                        .addNode("B", AsyncNodeAction.node_async(
                                agentMessageState -> {
                                    log.info("B method:{}", agentMessageState.data().getOrDefault("method", "default"));
                                    TaskSendParams taskSendParams =
                                            A2aTaskManager.getInstance().lastTaskParams(agentMessageState.sessionId().get());
                                    if (taskSendParams != null) {
                                        log.info("B: {}, taskSendParams:{}", agentMessageState, JSONObject.toJSONString(taskSendParams));
                                    } else {
                                        log.info("B: {}, taskSendParams:{}", agentMessageState, null);
                                    }
                                    return Map.of("next", "C", "method", "B default");
                                }))
                        .addNode("C", AsyncNodeAction.node_async(
                                agentMessageState -> {
                                    log.info("C method:{}", agentMessageState.data().getOrDefault("method", "default"));
                                    TaskSendParams taskSendParams =
                                            A2aTaskManager.getInstance().lastTaskParams(agentMessageState.sessionId().get());
                                    log.info("C: {}, taskSendParams:{}", agentMessageState, JSONObject.toJSONString(taskSendParams));
                                    if (taskSendParams != null) {
                                        log.info("lastTaskSendParams: {}",
                                                JSONObject.toJSONString(taskSendParams));
                                        return Map.of("next", "A", "method", "C default");
                                    } else {
                                        log.info("lastTaskSendParams: is null");
                                        return Map.of("next", "D", "method", "");
                                    }
                                }))
                        .addEdge(START, "A")
                        .addEdge("A", "B")
                        .addEdge("B", "C")
                        .addConditionalEdges("C",
                                edge_async(state ->
//                                        state.next().orElse("D")
                                                StringUtils.isEmpty(state.data().getOrDefault("method", "").toString()) ? "D" : "A"
                                ),
                                Map.of(
                                        "A", "A",
                                        "D", END
                                ));
        CompiledGraph<AgentMessageState> compile = graph.compile();
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(Uuid.uuid4hex())
                .build();
        AsyncGenerator<NodeOutput<AgentMessageState>> stream =
                compile.stream(Map.of(
                        "messages", UserMessage.from("帮我发起CR"),
                                "method", "humanInput"
                        ),
                        runnableConfig);
        for (NodeOutput<AgentMessageState> agentMessageStateNodeOutput : stream) {
            log.info("agentMessageStateNodeOutput: {}", agentMessageStateNodeOutput);
        }
    }

}
