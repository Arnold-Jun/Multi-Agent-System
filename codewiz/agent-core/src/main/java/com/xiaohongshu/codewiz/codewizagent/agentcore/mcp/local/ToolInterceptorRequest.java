package com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local;

import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.ToolInterceptorEnum;
import lombok.Data;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/10 12:40
 */
@Data
public class ToolInterceptorRequest {
    private String requestId;
    private String sessionId;
    private String name;
    private String text;
    private String toolInfo;
    private ToolInterceptorEnum type;
    private String userMessage;
}
