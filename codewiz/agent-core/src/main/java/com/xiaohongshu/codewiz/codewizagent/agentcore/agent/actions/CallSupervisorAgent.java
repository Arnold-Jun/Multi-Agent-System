package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.SupervisorAgent;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.state.AgentMessageState;
import com.xiaohongshu.codewiz.codewizagent.agentcore.common.PromptParseToObject;
import com.xiaohongshu.codewiz.codewizagent.agentcore.common.TodoList;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;
import org.bsc.langgraph4j.streaming.StreamingOutput;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/27 22:33
 */
@Slf4j
public class CallSupervisorAgent implements NodeAction<AgentMessageState> {

    final SupervisorAgent agent;

    final String agentName;

    String members = "";

    StreamingChatGenerator<AgentMessageState> generator = null;

    BlockingQueue<AsyncGenerator.Data<StreamingOutput<AgentMessageState>>> queue = null;

    private String requestId;
    private String username;

    /**
     * Constructs a SupervisorAgent with the specified agent.
     *
     * @param agent the agent to be associated with this SupervisorAgent
     */
    public CallSupervisorAgent(String agentName,  SupervisorAgent agent, List<String> members , String requestId, String username) {
        this.agent = agent;
        this.agentName = agentName;
        for (String member : members) {
            this.members += member + ",";
        }
        this.members = this.members.substring(0, this.members.length() - 1);
        this.requestId = requestId;
        this.username = username;
    }

    public void setQueue(
            BlockingQueue<AsyncGenerator.Data<StreamingOutput<AgentMessageState>>> queue) {
        this.queue = queue;
    }

    /**
     * Maps the result of the response from an AI message to a structured format.
     *
     * @param response the response containing the AI message
     * @return a map containing the agent's outcome
     * @throws IllegalStateException if the finish reason of the response is unsupported
     */
    private Map<String,Object> mapResult( ChatResponse response )  {

        var content = response.aiMessage();

        try {
            TeamMember teamMember = PromptParseToObject.parse(content.text(), TeamMember.class);
            String next = teamMember.getMemberName();
            SupervisorTaskSendParams taskSendParams = teamMember.getTaskSendParams();
            if (!checkTaskSendParams(taskSendParams)) {
                return Map.of("next", "FINISH", "messages", new AiMessage("流程异常终止，Agent调用异常，请联系管理员，content:" + content));
            }

            // 当前todoList与agent+skill绑定
            String todoList = "";
            for (String skill : taskSendParams.getSkills()) {
                todoList = "阶段：" + skill + "\n======\n" + TodoList.getInstance().get(taskSendParams.getAgentInvoke() + "__" + skill) + "\n======\n";
            }

            if (StringUtils.isNoneEmpty(todoList)) {
                return Map.of( "next", next,
                        "messages", new AiMessage(todoList),
                        "agent_response", "",
                        "nextAgent", taskSendParams.getAgentInvoke(),
                        "skills", taskSendParams.getSkills(),
                        "username", username
                        );
            } else {
                return Map.of( "next", next, "agent_response", "");
            }
        } catch (Exception e) {
            log.error("parse next error, response:{}", JSONObject.toJSONString(response), e);
        }

        return Map.of("FINISH","FINISH");
//        throw new IllegalStateException("Unsupported finish reason: " + response.finishReason() );
    }

    private boolean checkTaskSendParams(SupervisorTaskSendParams taskSendParams) {
        if (StringUtils.isEmpty(taskSendParams.getAgentInvoke())) {
            log.error("taskSendParams invoke agent is empty");
            return false;
        }
        if (CollectionUtil.isEmpty(taskSendParams.getMessage())) {
            log.error("taskSendParams message is empty");
            return false;
        }
        return true;
    }

    /**
     * Maps the result of the response from an AI message to a structured format.
     *
     * @param response the response containing the AI message
     * @return a map containing the agent's outcome
     * @throws IllegalStateException if the finish reason of the response is unsupported
     */
    private Map<String,Object> mapResultWithStream( ChatResponse response )  {

        var content = response.aiMessage();

        try {
            String next = JSONObject.parseObject(content.text()).getString("next");
            return Map.of( "next", next, "todoList", TodoList.getInstance().get(next) );
        } catch (Exception e) {
            log.error("parse next error, response:{}", JSONObject.toJSONString(response), e);
        }

        return Map.of("FINISH","FINISH");
//        throw new IllegalStateException("Unsupported finish reason: " + response.finishReason() );
    }

    @Override
    public Map<String, Object> apply(AgentMessageState state) throws Exception {
        log.info( "supervisorAgent" );
        var messages = state.messages();

        if( messages.isEmpty() ) {
            throw new IllegalArgumentException("no input provided!");
        }


        if( agent.isStreaming()) {

            if (generator == null) {
                generator = StreamingChatGenerator.<AgentMessageState>builder()
                        .queue(queue)
                        .mapResult( this::mapResult )
                        .startingNode(agentName)
                        .startingState( state )
                        .build();
            }

            agent.execute(messages, generator.handler(), members);

            return Map.of( "_generator", generator);

        }
        else {
            var response = agent.execute(messages, members);
            return mapResult(response);
        }

    }
}
