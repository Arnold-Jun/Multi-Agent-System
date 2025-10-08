package com.xiaohongshu.codewiz.codewizagent.cragent.mcp;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HostAndPort;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.config.McpConfigManager;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.discovery.McpServiceDiscovery;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.discovery.RoundLoadBalance;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local.LoadMcpRequest;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local.LoadMcpServerRequest;
import com.xiaohongshu.infra.xds.eds.Instance;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
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

    private Map<String, List<McpClient>> mcpClientListMap = new ConcurrentHashMap<>();
    private Map<String, LoadMcpServerRequest> mcpLoadMap = new ConcurrentHashMap<>();
    private Map<String, LoadMcpServerRequest> mcpLoadErrorMap = new ConcurrentHashMap<>();

    private Thread healthCheck;

    @Resource
    private McpServiceDiscovery mcpServiceDiscovery;

    private RoundLoadBalance<McpClient> loadBalance;

    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        objectMapper = new ObjectMapper();
        loadBalance = new RoundLoadBalance<>();
        try {
            LoadMcpRequest crAgentMcpServer = McpConfigManager.getMcpConfig("crAgent");
            loadMcpClient(crAgentMcpServer);
        } catch (Exception e) {
            log.error("load mcp server error, " , e);
        }
        healthCheck = new Thread(new McpHealthChecker());
        healthCheck.start();
    }

    /**
     * 测试使用的运行工具类
     * @param payload
     * @return
     */
    public String runClientMethod(Object payload) {
        log.info("runClientMethod: " + payload);
        List<McpClient> mcpClients = mcpClientListMap.get("cr-mcp-server");
        McpClient client = loadBalance.next("cr-mcp-server", mcpClients);
        return client.executeTool((ToolExecutionRequest) payload);
    }

    /**
     * 执行工具，使用负载均衡器选择一个McpClient来执行工具
     *
     * @param mcpServerName
     * @param request
     * @param memoryId
     * @return
     */
    public String executeTool(String mcpServerName, ToolExecutionRequest request, Object memoryId) throws JsonProcessingException {
        log.info("executeTool: memoryId:{}, mcpServerName:{}", memoryId, mcpServerName);
        List<McpClient> mcpClients = mcpClientListMap.get(mcpServerName);
        McpClient client = loadBalance.next(mcpServerName, mcpClients);
        return client.executeTool(request);
    }

    /**
     * 执行工具，带重试机制
     * @param mcpServerName
     * @param request
     * @param memoryId
     * @return
     */
    public String executeToolWithRetry(String mcpServerName, ToolExecutionRequest request, Object memoryId) {
        for (int i = 0; i < 3; i++) {
            try {
                return executeTool(mcpServerName, request, memoryId);
            } catch (Exception e) {
                log.error("executeTool error, mcpServerName: {}, request: {}", mcpServerName, JSONObject.toJSONString(request), e);
            }
        }
        log.error("mcpServerName: {}, executeTool failed after 3 retries", mcpServerName);
        throw new RuntimeException("mcpServerName:" + mcpServerName + " executeTool failed after 3 retries");
    }

    public Map<String, ToolProvider> getToolProviderMap() {
        return mcpClientListMap.entrySet().stream().map(entry -> {
            String name = entry.getKey();
            List<McpClient> mcpClients = entry.getValue();
            return McpToolLBProvider.builder()
                    .mcpServerName(name)
                    .mcpClientManager(this)
                    .failIfOneServerFails(false)
                    .mcpClients(mcpClients)
                    .build();
        }).collect(Collectors.toMap(
                McpToolLBProvider::getMcpServerName,
                value -> value,
                (a, b) -> a));
    }

    public boolean loadMcpClient(LoadMcpRequest loadMcpRequest) {
        Set<Map.Entry<String, LoadMcpServerRequest>> entries = loadMcpRequest.getMcpServers().entrySet();
        for (Map.Entry<String, LoadMcpServerRequest> entry : entries) {
            LoadMcpServerRequest loadMcpServerRequest = entry.getValue();
            String serverName = entry.getKey();
            mcpLoadMap.put(serverName, loadMcpServerRequest);
            if (StringUtils.isNoneEmpty(loadMcpServerRequest.getCommand())) {
                loadStdMcpClient(serverName, loadMcpServerRequest);
            } else if (StringUtils.isNoneEmpty(loadMcpServerRequest.getUrl())) {
                loadSseMcpClient(serverName, loadMcpServerRequest);
            } else {
                log.error("loadMcpClient error, serverName or url is empty, serverName: {}, loadMcpServerRequest: {}",
                        serverName, JSONObject.toJSONString(loadMcpServerRequest));
                throw new IllegalArgumentException("Invalid type: command or url is required");
            }
        }
        return true;
    }

    public void loadSseMcpClient(String name, LoadMcpServerRequest loadMcpServerRequest) {
        String sseUrl = loadMcpServerRequest.getUrl();
        try {
            List<String> urls = new ArrayList<>();
            if (sseUrl.startsWith("eds://")) {
                // 服务发现
                String serviceName = sseUrl.substring("eds://".length());
                mcpServiceDiscovery.addServiceName(serviceName);
                List<Instance> services = mcpServiceDiscovery.getServices(serviceName, "", "");
                services.forEach(instance -> {
                    HostAndPort hostAndPort = instance.getHostAndPort();
                    String url = hostAndPort.getHost() + ":" + hostAndPort.getPort() + "/sse";
                    if (!url.startsWith("http")) {
                        url = "http://" + url;
                    }
                    urls.add(url);
                });
            } else {
                urls.add(sseUrl);
            }

            for (String url : urls) {
                try {
                    HttpMcpTransport.Builder builder = new HttpMcpTransport.Builder()
                            .sseUrl(url)
                            .timeout(Duration.of(0, ChronoUnit.SECONDS))
                            .logRequests(true)
                            .logResponses(true);
                    McpTransport transport = new HttpMcpTransport(builder);

                    load(name, transport);
                    mcpLoadErrorMap.remove(name + "__" + url);
                } catch (Exception e) {
                    log.error("load sse http mcp transport error, name:{}, sseUrl:{}, url: {}",name, sseUrl, url, e);
                    mcpLoadErrorMap.put(name + "__" + url, loadMcpServerRequest);
                }
            }
        } catch (Exception e) {
            log.error("load sse mcp client error, name:{}, sseUrl:{}", name, sseUrl, e);
            mcpLoadErrorMap.put(name + "__" + sseUrl, loadMcpServerRequest);
        }
    }

    public void reloadSseMcpClient(String name, String url, LoadMcpServerRequest loadMcpServerRequest) {
        if (StringUtils.isEmpty(url)) {
            loadSseMcpClient(name, loadMcpServerRequest);
            return;
        }
        try {
            HttpMcpTransport.Builder builder = new HttpMcpTransport.Builder()
                    .sseUrl(url)
                    .timeout(Duration.of(0, ChronoUnit.SECONDS))
                    .logRequests(true)
                    .logResponses(true);
            McpTransport transport = new HttpMcpTransport(builder);

            load(name, transport);
            mcpLoadErrorMap.remove(name + "__" + url);
        } catch (Exception e) {
            log.error("reload sse http mcp transport error, name:{}, url: {}",name, url, e);
        }
    }

    public void loadStdMcpClient(String name, LoadMcpServerRequest loadMcpServerRequest) {
        String command = loadMcpServerRequest.getCommand();
        String[] args = loadMcpServerRequest.getArgs();
        Map<String, String> envs = loadMcpServerRequest.getEnv();
        try {
            List<String> commandList = new ArrayList<>();
            commandList.add(command);
            commandList.addAll(Arrays.asList(args));
            McpTransport transport = new StdioMcpTransport.Builder()
                    .command(commandList)
                    .environment(envs)
                    .logEvents(true)
                    .build();

            load(name, transport);
            mcpLoadErrorMap.remove(name);
        } catch (Exception e) {
            log.error("load std mcp client error, name:{}, command:{}", name, command ,e);
            mcpLoadErrorMap.put(name, loadMcpServerRequest);
        }
    }

    public void load(String name, McpTransport transport) {
        McpClient mcpClient = new DefaultMcpClient.Builder()
                .clientName(name)
                .clientVersion("1.0.0")
                .logHandler(new HttpMcpLogMessageHandler(name))
                .transport(transport)
                .build();

        List<McpClient> mcpClients = mcpClientListMap.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>());
        mcpClients.add(mcpClient);
    }

    public class McpHealthChecker implements Runnable {

        private Map<String, AtomicInteger> mcpErrorCount = new ConcurrentHashMap<>();

        @Override
        public void run() {
            while (true) {

                for (Map.Entry<String, LoadMcpServerRequest> errorServerRequest : mcpLoadErrorMap.entrySet()) {
                    String serviceName = errorServerRequest.getKey();
                    LoadMcpServerRequest loadMcpServerRequest = errorServerRequest.getValue();
                    String[] serviceNameArr = serviceName.split("__");
                    if (serviceNameArr.length == 2) {
                        reloadSseMcpClient(serviceName, serviceNameArr[1], loadMcpServerRequest);
                    } else {
                        loadStdMcpClient(serviceName, loadMcpServerRequest);
                    }
                }

                for (Map.Entry<String, List<McpClient>> listEntry : mcpClientListMap.entrySet()) {
                    for (McpClient mcpClient : listEntry.getValue()) {
                        String name = listEntry.getKey();
                        try {
                            List<ToolSpecification> toolSpecifications = mcpClient.listTools();
                            log.info("mcp health check, name:{}, alive", name);
                        } catch (Exception e) {
                            log.error("mcp health check error, name:{}", name, e);
                            LoadMcpServerRequest loadMcpServerRequest = mcpLoadMap.get(name);
                            mcpLoadErrorMap.put(name, loadMcpServerRequest);
                        }
                    }
                }
                try {
                    // 10s的间隔
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    log.error("McpClient health check interrupted", e);
                }
            }
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
