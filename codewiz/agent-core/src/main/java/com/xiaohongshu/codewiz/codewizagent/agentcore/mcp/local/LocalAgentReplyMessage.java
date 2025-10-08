package com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local;

import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalToolMessageRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalToolMessageResponse;
import lombok.Data;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/24 11:12
 */
@Data
public class LocalAgentReplyMessage {

    private LocalToolMessageRequest request;
    private LocalToolMessageResponse response;

}
