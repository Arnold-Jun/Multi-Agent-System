package com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local;

import java.util.Map;
import lombok.Data;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/1 11:40
 */
@Data
public class LoadMcpServerRequest {
    /**
     * 用于command模式的指令
     */
    private String command;
    /**
     * 用于sse模式的url
     */
    private String url;
    private String[] args;
    private Map<String, String> env;

}
