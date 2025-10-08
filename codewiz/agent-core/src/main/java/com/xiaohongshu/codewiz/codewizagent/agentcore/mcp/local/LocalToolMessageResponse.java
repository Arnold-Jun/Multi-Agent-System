package com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local;

import java.util.List;
import lombok.Data;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/9 23:30
 */
@Data
public class LocalToolMessageResponse {

    private String requestId;
    private String memoryId;
    private List<LocalExecuteToolMessageResponse> tools;
}
