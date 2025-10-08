package com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.config;

import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LoadMcpRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LoadMcpServerRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/10 21:00
 */
@Slf4j
public class McpConfigManager {

    public static final McpConfigManager INSTANCE = new McpConfigManager();

    private Map<String, LoadMcpRequest> systemMcpServers;
    private Map<String, LoadMcpRequest> userMcpServers;

    private McpConfigManager() {
        systemMcpServers = new ConcurrentHashMap<>();
        userMcpServers = new ConcurrentHashMap<>();

        LoadMcpRequest crAgentMcp = new LoadMcpRequest();
        LoadMcpServerRequest crMcpServer = new LoadMcpServerRequest();
        crMcpServer.setUrl("http://127.0.0.1:18082/sse"); // langchain
//        crMcpServer.setUrl("http://127.0.0.1:18082"); // spring-ai
        crAgentMcp.setMcpServers(Map.of("cr-mcp-server", crMcpServer));

        LoadMcpRequest commonLocal = new LoadMcpRequest();
        LoadMcpServerRequest localMcpServer = new LoadMcpServerRequest();
        localMcpServer.setCommand("/Users/liuyanghong/workspace/code/llm/local-mcp-server/local-mcp-server");
//        String[] localMcpServerArgs = new String[5];
//        localMcpServerArgs[0] = "-Dspring.ai.mcp.server.transport=STDIO";
//        localMcpServerArgs[1] = "-Dspring.main.web-application-type=none";
//        localMcpServerArgs[2] = "-Dlogging.pattern.console=";
//        localMcpServerArgs[3] = "-jar";
//        localMcpServerArgs[4] = "/Users/liuyanghong/workspace/code/llm/agentlocalserver/mcp-server/local-mcp-server-1.0.0-SNAPSHOT.jar";
//        localMcpServerArgs[4] = "/tmp/local-mcp-server-1.0.0-SNAPSHOT.jar";
//        localMcpServer.setArgs(localMcpServerArgs);
//        Map<String, String> env = new HashMap<>();
//        env.put("LOCAL_MCP_WORKSPACE", "");
//        localMcpServer.setEnv(env);

        String[] localMcpServerArgs = new String[1];
        localMcpServerArgs[0] = "-Dlogging.pattern.console=";
        localMcpServer.setArgs(localMcpServerArgs);
        commonLocal.setMcpServers(Map.of("local-mcp-server", localMcpServer));
        systemMcpServers.put("crAgent", crAgentMcp);
        systemMcpServers.put("commonLocal", commonLocal);
    }

    public static LoadMcpRequest getMcpConfig(String name) {
        return INSTANCE.systemMcpServers.get(name);
    }

}
