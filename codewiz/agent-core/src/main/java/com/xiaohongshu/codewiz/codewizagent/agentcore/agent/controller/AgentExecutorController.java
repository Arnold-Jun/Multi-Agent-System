package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.controller;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.AgentChatRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.AgentOperateRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.OperateTypeEnum;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.ToolService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.swagger.annotations.Api;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/26 21:26
 */
@RestController
@Api(tags = "Agent控制器")
@RequestMapping("/agentexecutor")
@Validated
@ResponseBody
@Slf4j
public class AgentExecutorController {

    private StateGraph<AgentExecutor.State> stateGraph;

    private Map<String, CompiledGraph<AgentExecutor.State>> compiledGraphCache;

    @PostConstruct
    public void init() throws GraphStateException {
        OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
                .baseUrl("http://redservingapi.devops.xiaohongshu.com/v1")
                .apiKey("QSTaacc44c2c29fd5647e708d612836f32f")
                .modelName("qwen2.5-72b-funtion-tool")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();
        log.info("AgentController init");
        stateGraph = AgentExecutor.graphBuilder()
                .chatLanguageModel(openAiChatModel)
                .toolSpecification(new ToolService())
                .build();


        compiledGraphCache = new ConcurrentHashMap<>();
    }

    @PostMapping("/chat")
    public String chat(@RequestBody AgentChatRequest request) throws GraphStateException {
        log.info("chat request: {}", JSONObject.toJSON(request));
        String sessionId = "123123";
        CompiledGraph<AgentExecutor.State> graph = compiledGraphCache.getOrDefault(sessionId, stateGraph.compile());
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
        AsyncGenerator<NodeOutput<AgentExecutor.State>> messages = graph.stream(Map.of("messages", UserMessage.from(text)));
        for (NodeOutput<AgentExecutor.State> message : messages) {
            log.info("message: {}", message);
        }
        return "chat";
    }

}
