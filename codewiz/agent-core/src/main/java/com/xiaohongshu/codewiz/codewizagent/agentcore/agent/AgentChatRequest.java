package com.xiaohongshu.codewiz.codewizagent.agentcore.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;
import lombok.Data;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/27 11:55
 */
@Data
public class AgentChatRequest {

    private String requestId;
    private String sessionId;
    private String chat;
    private List<AgentOperateRequest> operates;
    private List<ToolSpecification> userTools;
    private String workspace;

}
