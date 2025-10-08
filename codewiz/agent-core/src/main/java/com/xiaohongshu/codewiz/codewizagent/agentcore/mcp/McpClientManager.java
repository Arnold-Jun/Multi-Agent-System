package com.xiaohongshu.codewiz.codewizagent.agentcore.mcp;

import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.config.McpConfigManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LoadMcpRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LoadMcpServerRequest;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.logging.McpLogLevel;
import dev.langchain4j.mcp.client.logging.McpLogMessage;
import dev.langchain4j.mcp.client.logging.McpLogMessageHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/28 10:33
 */
@Slf4j
@Component
public class McpClientManager {

    private Map<String, McpClient> mcpClientMap = new ConcurrentHashMap<>();
    private Map<String, ToolProvider> toolProviderMap = new ConcurrentHashMap<>();
    private Map<String, McpTransport> mcpTransportMap = new ConcurrentHashMap<>();
    private Map<String, McpTransport> mcpErrorTransportMap = new ConcurrentHashMap<>();

//    private Map<String, McpSyncClient> mcpSrpingClientMap = new ConcurrentHashMap<>();
//    private Map<String, McpClientTransport> toolSpringProviderMap = new ConcurrentHashMap<>();

    private Thread healthCheck;

    @PostConstruct
    public void init() {
//        try {
//            LoadMcpRequest crAgentMcpServer = McpConfigManager.getMcpConfig("crAgent");
//            loadMcpClient(crAgentMcpServer);
//        } catch (Exception e) {
//            log.error("load mcp server error, " , e);
//        }
        healthCheck = new Thread(() -> {
            while (true) {
//                for (Map.Entry<String, McpSyncClient> stringMcpSyncClientEntry : mcpSrpingClientMap.entrySet()) {
//                    Object ping = stringMcpSyncClientEntry.getValue().ping();
//                    log.info("mcpSrpingClientMap, ping:{}" ,ping);
//                }
                List<String> successTransportList = new ArrayList<>();
                for (Map.Entry<String, McpTransport> errorTransport : mcpErrorTransportMap.entrySet()) {
                    String name = errorTransport.getKey();
                    try {
                        McpTransport transport = errorTransport.getValue();
                        load(name, transport);
                        successTransportList.add(name);
                    } catch (Exception e) {
                        log.error("mcp health check error, name:{}", name, e);
                    }
                }
                successTransportList.forEach(transportName -> mcpErrorTransportMap.remove(transportName));
                for (Map.Entry<String, McpClient> entry : mcpClientMap.entrySet()) {
                    String name = entry.getKey();
                    try {
                        McpClient mcpClient = entry.getValue();
                        if (mcpClient instanceof HttpMcpTransport) {

                        }
                        List<ToolSpecification> toolSpecifications = mcpClient.listTools();
                        log.info("mcp health check, name:{}, alive", name);
                    } catch (Exception e) {
                        log.error("mcp health check error, name:{}", name, e);
                        retryReload(entry.getKey(), mcpTransportMap.get(name));
                    }
                }
                try {
                    // 10s的间隔
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    log.error("McpClient health check interrupted", e);
                }
            }
        });
        healthCheck.start();
    }

    /**
     * 测试使用的运行工具类
     * @param payload
     * @return
     */
    public String runClientMethod(Object payload) {
        log.info("runClientMethod: " + payload);
        McpClient mcpClient = mcpClientMap.get("cr-mcp-server");
        return mcpClient.executeTool((ToolExecutionRequest) payload);

//        McpSyncClient mcpSyncClient = mcpSrpingClientMap.get("cr-mcp-server");
//        McpSchema.ListToolsResult listToolsResult = mcpSyncClient.listTools();
//        List<McpSchema.Tool> tools = listToolsResult.tools();
//        McpSchema.CallToolResult callToolResult = mcpSyncClient.callTool((McpSchema.CallToolRequest) payload);
//        return callToolResult.toString();
    }

    public ToolProvider getToolProvider(String toolName) {
        return toolProviderMap.get(toolName);
    }

    public Map<String, ToolProvider> getToolProviderMap() {
        return toolProviderMap;
    }

    public boolean loadMcpClient(LoadMcpRequest loadMcpRequest) {
        Set<Map.Entry<String, LoadMcpServerRequest>> entries = loadMcpRequest.getMcpServers().entrySet();
        for (Map.Entry<String, LoadMcpServerRequest> entry : entries) {
            LoadMcpServerRequest loadMcpServerRequest = entry.getValue();
            String serverName = entry.getKey();
            if (StringUtils.isNoneEmpty(loadMcpServerRequest.getCommand())) {
                loadStdMcpClient(serverName, loadMcpServerRequest.getCommand(), loadMcpServerRequest.getArgs(), loadMcpServerRequest.getEnv());
            } else if (StringUtils.isNoneEmpty(loadMcpServerRequest.getUrl())) {
                loadSseMcpClient(serverName, loadMcpServerRequest.getUrl());
            } else {
                log.error("loadMcpClient error, serverName or url is empty, serverName: {}, loadMcpServerRequest: {}",
                        serverName, JSONObject.toJSONString(loadMcpServerRequest));
                throw new IllegalArgumentException("Invalid type: command or url is required");
            }
        }
        return true;
    }

    public void loadSseMcpClient(String name, String sseUrl) {
        HttpMcpTransport.Builder builder = new HttpMcpTransport.Builder()
                .sseUrl(sseUrl)
                .timeout(Duration.of(0, ChronoUnit.SECONDS))
                .logRequests(true)
                .logResponses(true);
        McpTransport transport = new HttpMcpTransport(builder);

        load(name, transport);

//        McpClientTransport mcpClientTransport = HttpClientSseClientTransport.builder(sseUrl).build();
//        loadSpringAi(name, mcpClientTransport);

    }

    public void loadStdMcpClient(String name, String command, String[] args, Map<String, String> envs) {
        List<String> commandList = new ArrayList<>();
        commandList.add(command);
        commandList.addAll(Arrays.asList(args));
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(commandList)
                .environment(envs)
                .logEvents(true)
                .build();

        load(name, transport);
    }

    public void retryReload(String name, McpTransport transport) {
        try {
            log.info("retryReload: name:{}, transport:{}", name, transport);
            load(name, transport);
            log.info("retryReload success: name:{}", name);
        } catch (Exception e) {
            log.error("retryReload error, name:{}", name, e);
        }
    }

//    public void loadSpringAi(String name, McpClientTransport mcpClientTransport) {
//        McpSchema.Implementation clientInfo = new McpSchema.Implementation("spring-ai-mcp-client" + name, "1.0.0");
//        io.modelcontextprotocol.client.McpClient.SyncSpec syncSpec =
//                io.modelcontextprotocol.client.McpClient.sync(mcpClientTransport)
//                        .clientInfo(clientInfo)
//                        .requestTimeout(Duration.of(60, ChronoUnit.SECONDS));
//        McpSyncClient syncClient = syncSpec.build();
//
//        syncClient.initialize();


//        toolSpringProviderMap.put(name, mcpClientTransport);
//        mcpSrpingClientMap.put(name, syncClient);

//    }

    public void load(String name, McpTransport transport) {
        if (transport == null) {
            log.error("load: transport is null");
            return;
        }
        try {
            mcpTransportMap.put(name, transport);

            McpClient mcpClient = new DefaultMcpClient.Builder()
                    .clientName(name)
                    .clientVersion("1.0.0")
                    .logHandler(new HttpMcpLogMessageHandler(name))
                    .transport(transport)
                    .build();

            mcpClientMap.put(name, mcpClient);

            ToolProvider toolProvider = McpToolProvider.builder()
                    .mcpClients(List.of(mcpClient))
                    .build();
            toolProviderMap.put(name, toolProvider);
        } catch (Exception e) {
            log.error("load error, name:{}", name, e);
            mcpErrorTransportMap.put(name, transport);
            throw e;
        }

    }

    public static class HttpMcpLogMessageHandler implements McpLogMessageHandler {

        private String clientName;

        public HttpMcpLogMessageHandler(String clientName) {
            this.clientName = clientName;
        }

        @Override
        public void handleLogMessage(McpLogMessage mcpLogMessage) {
            McpLogLevel level = mcpLogMessage.level();
            switch (level) {
                case DEBUG:
                    log.debug("[{}] HttpMcpLogMessageHandler {}", clientName, mcpLogMessage.data().asText());
                    break;
                case WARNING:
                    log.warn("[{}] HttpMcpLogMessageHandler {}", clientName, mcpLogMessage.data().asText());
                    break;
                case ERROR:
                    log.error("[{}] HttpMcpLogMessageHandler {}", clientName, mcpLogMessage.data().asText());
                    break;
                default:
                    log.info("[{}] HttpMcpLogMessageHandler {}", clientName, mcpLogMessage.data().asText());
            }
        }
    }

}
