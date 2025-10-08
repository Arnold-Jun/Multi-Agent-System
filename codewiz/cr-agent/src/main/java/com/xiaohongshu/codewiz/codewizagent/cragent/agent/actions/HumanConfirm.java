package com.xiaohongshu.codewiz.codewizagent.cragent.agent.actions;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.state.AgentMessageState;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.ToolInterceptor;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.ToolsInterceptorUtil;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bsc.langgraph4j.action.NodeAction;

/**
 * <p>
 *  The HumanConfirm class implements the NodeAction interface for handling
 *  actions related to ask human for confirm to do next tools within an agent's context.
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/10 11:38
 */
@Slf4j
public class HumanConfirm implements NodeAction<AgentMessageState> {

    /**
     * Applies the human confirm logic based on the provided agent state.
     *
     * @param state the current state of the agent executor
     * @return a map containing the intermediate steps of the execution
     * @throws IllegalArgumentException if no agent outcome is provided
     * @throws IllegalStateException if no action or tool is found for execution
     */
    @Override
    public Map<String, Object> apply(AgentMessageState state) throws Exception {
        log.info("Human confirm action start");
        Map<String, Object> result = null;
        Optional<ChatMessage> chatMessageOpt = state.lastMessage();
        if (chatMessageOpt.isEmpty()) {
            result = Map.of("FINISH", "FINISH");
            return result;
        }
        ChatMessage chatMessage = chatMessageOpt.get();
        if (chatMessage instanceof UserMessage) {
            UserMessage message = (UserMessage) chatMessage;
            // 定义这里的name是工具名称
            ToolInterceptor toolInterceptor = ToolsInterceptorUtil.getInterceptor(message.name());
//            String userText = message.singleText();
            String singleText = message.singleText();
            String userText = "finish";
            try {
                JSONObject jsonObject = JSON.parseObject(singleText);
                userText = jsonObject.getString("userMessage");
            } catch (Exception e) {
                log.error("userText error, chatMessage:{}", chatMessage, e);
            }
            switch (toolInterceptor.getType()) {
                case CONFIRM:
                    log.info("human confirm: tool:{}, text:{}", toolInterceptor.getName(), userText);
                    if ("yes".equals(userText) || "confirm".equals(userText) || "apply".equals(userText)) {
                        result = Map.of("next", "action");
                    }
                    if ("finish".equals(userText) || "cancel".equals(userText) || "no".equals(userText)) {
                        result = Map.of("next", "FINISH");
                    }
                    if ("back".equals(userText) || "ignore".equals(userText)) {
                        result = Map.of("next", "callback");
                    }
                    break;
                case TEXT:
                    log.info("human text input: tool:{}, text:{}",  toolInterceptor.getName(), userText);
                    if (StringUtils.isNoneEmpty(userText)) {
                        result = Map.of("next", "action", "messages", UserMessage.from(userText));
                    } else {
                        result = Map.of("next", "loop");
                    }
                    break;
                case CHOICE:
                    log.info("human choice input: tool:{}, text:{}", toolInterceptor.getName(), userText);
                    try {
//                        JSONArray objects = JSON.parseArray(userText);
//                        if (!objects.isEmpty()) {
//                            result = Map.of("next", "action", "messages", chatMessage);
//                        } else {
//                            result = Map.of("next", "loop");
//                        }
                        result = Map.of("next", "action", "messages", chatMessage);
                    } catch (Exception e) {
                        log.error("human choice error: userText:{}", userText, e);
                        result = Map.of("next", "FINISH");
                    }
                    break;
                default:
                    log.error("unsupport tool type: {}", toolInterceptor.getType());
                    result = Map.of("next", "FINISH");
                    break;
            }
        }
        if (result == null) {
            result = Map.of("next", "FINISH");
        }
        log.info("Human confirm action :{}", result);
        return result;
    }
}
