package com.xiaohongshu.codewiz.codewizagent.agentcore.mcp;

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
public class ToolInterceptor {

    private String id;
    private String requestId;
    private String name;
    private String text;
    private String toolInfo;
    private ToolInterceptorEnum type;
}
