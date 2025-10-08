package com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local;

import java.util.Map;
import lombok.Data;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/31 21:10
 */
@Data
public class LoadMcpRequest {
    private Map<String, LoadMcpServerRequest> mcpServers;
}
