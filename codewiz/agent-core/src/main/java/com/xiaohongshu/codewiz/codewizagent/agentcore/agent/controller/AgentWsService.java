package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.controller;

import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.AgentChatRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalAgentReplyMessage;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.ToolInterceptorRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.json.JsonRpcMethod;
import java.util.LinkedHashMap;

/**
 * <p>
 * 当前的Agentws定义，每个方法的结尾必定跟着一个userEmail。用于显示传递
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/21 19:12
 */
public interface AgentWsService {

    @JsonRpcMethod("/agent/chat")
    void chat(LinkedHashMap map, String userEmail);

    void chat(AgentChatRequest request, String userEmail);

    @JsonRpcMethod("/agent/action")
    void action(LinkedHashMap map, String userEmail);

    void action(LocalAgentReplyMessage localAgentReplyMessage, String userEmail);

    @JsonRpcMethod("/agent/human/confirm")
    void humanConfirm(LinkedHashMap map, String userEmail);

    void humanConfirm(ToolInterceptorRequest toolInterceptorRequest, String userEmail);

    @JsonRpcMethod("/agent/human/replay")
    void humanReplay(LinkedHashMap map, String userEmail);

    void humanReplay(ToolInterceptorRequest toolInterceptorRequest, String userEmail);

    @JsonRpcMethod("/agent/human/input")
    void humanInput(LinkedHashMap map, String userEmail);

    @JsonRpcMethod("/agent/ping")
    String ping();

    void humanInput(AgentChatRequest request, String userEmail);
}
