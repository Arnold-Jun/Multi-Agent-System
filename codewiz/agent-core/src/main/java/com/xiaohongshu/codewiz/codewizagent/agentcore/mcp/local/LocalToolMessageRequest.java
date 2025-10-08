package com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local;

import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalExecuteToolMessageRequest;
import java.util.List;
import lombok.Data;

/**
 * <p>
 * agent转发到本地的工具请求
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/9 22:29
 */
@Data
public class LocalToolMessageRequest {

    private String requestId;
    private String memoryId;
    private List<LocalExecuteToolMessageRequest> tools;

}
