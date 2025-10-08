package com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local;

import lombok.Data;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/9 22:29
 */
@Data
public class LocalExecuteToolMessageRequest {

    private String id;
    private String name;
    private String arguments;

}
