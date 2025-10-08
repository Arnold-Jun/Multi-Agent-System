package com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local;

import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.ToolInterceptorEnum;
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
    private String name;
    private String text;
    private String toolInfo;
    private ToolInterceptorEnum type;
    private String userMessage;
}
