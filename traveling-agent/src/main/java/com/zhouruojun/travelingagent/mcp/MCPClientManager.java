package com.zhouruojun.travelingagent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP客户端管理器
 * 管理traveling-agent与MCP服务器的连接和工具调用
 * 使用标准的HTTP JSON-RPC协议
 */
@Slf4j
@Component
public class MCPClientManager {

    private final Map<String, WebClient> mcpClientMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 可配置的超时时间（秒）
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int TOOL_CALL_TIMEOUT_SECONDS = 60; // 工具调用超时时间

    @PostConstruct
    public void init() {
        try {
            loadMCPClients();
            log.info("MCP客户端管理器初始化完成");
        } catch (Exception e) {
            log.error("MCP客户端管理器初始化失败", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        mcpClientMap.clear();
    }

    /**
     * 加载MCP客户端配置
     */
    private void loadMCPClients() {
        // 加载traveling-mcp-server客户端 - 使用MCP协议端点
        loadHttpMCPClient("traveling-mcp-server", "http://localhost:18083/mcp/jsonrpc");
        
        // 可以添加更多MCP服务器
        // loadHttpMCPClient("other-mcp-server", "http://other-server:port/mcp/jsonrpc");
    }

    /**
     * 加载HTTP MCP客户端
     */
    private void loadHttpMCPClient(String serverName, String jsonRpcUrl) {
        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(jsonRpcUrl)
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                    .build();
            
            mcpClientMap.put(serverName, webClient);
            
        } catch (Exception e) {
            log.error("加载MCP客户端失败: {} -> {}", serverName, jsonRpcUrl, e);
        }
    }

    /**
     * 执行工具调用
     */
    public String executeTool(String serverName, ToolExecutionRequest request) {
        
        WebClient webClient = mcpClientMap.get(serverName);
        if (webClient == null) {
            throw new RuntimeException("MCP服务器未找到: " + serverName);
        }
        
        try {
            // 处理工具参数
            Object argumentsObj = request.arguments();
            Map<String, Object> arguments;
            
            if (argumentsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> argsMap = (Map<String, Object>) argumentsObj;
                arguments = argsMap;
            } else if (argumentsObj instanceof String) {
                // 如果arguments是JSON字符串，尝试解析
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsedArgs = objectMapper.readValue((String) argumentsObj, Map.class);
                    arguments = parsedArgs;
                } catch (Exception e) {
                    log.warn("无法解析JSON参数: {}, 使用空参数", argumentsObj, e);
                    arguments = new HashMap<>();
                }
            } else {
                // 如果arguments不是Map或String类型，创建一个空的Map
                log.warn("工具参数类型不支持: {}, 使用空参数", argumentsObj != null ? argumentsObj.getClass().getSimpleName() : "null");
                arguments = new HashMap<>();
            }
            
            // 直接使用LLM传递的参数，不进行任何转换
            
            // 构建MCP工具调用请求
            Map<String, Object> mcpRequest = Map.of(
                "jsonrpc", "2.0",
                "id", System.currentTimeMillis(),
                "method", "tools/call",
                "params", Map.of(
                    "name", request.name(),
                    "arguments", arguments
                )
            );
            
            String response = webClient.post()
                    .bodyValue(mcpRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(TOOL_CALL_TIMEOUT_SECONDS))
                    .doOnError(error -> log.error("MCP工具调用网络错误: server={}, tool={}", serverName, request.name(), error))
                    .block();
            
            if (response == null || response.trim().isEmpty()) {
                throw new RuntimeException("MCP工具调用返回空响应");
            }
            
            // 解析响应
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            
            if (result.containsKey("result")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultData = (Map<String, Object>) result.get("result");
                
                if (resultData.containsKey("content")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> content = (List<Map<String, Object>>) resultData.get("content");
                    
                    if (content != null && !content.isEmpty()) {
                        Object firstContent = content.get(0);
                        if (firstContent instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> contentMap = (Map<String, Object>) firstContent;
                            Object textObj = contentMap.get("text");
                            
                            if (textObj != null) {
                                // text字段可能是字符串或对象（Map/JSONObject），需要正确转换
                                String text;
                                if (textObj instanceof String) {
                                    text = (String) textObj;
                                } else {
                                    // 如果是对象，转换为JSON字符串
                                    text = objectMapper.writeValueAsString(textObj);
                                }
                                
                                if (text != null && !text.isEmpty()) {
                                    return text;
                                } else {
                                    return "工具执行完成，但返回内容为空";
                                }
                            } else {
                                return "工具执行完成，但返回内容为空";
                            }
                        } else {
                            return "工具执行完成，但响应格式异常";
                        }
                    } else {
                        return "工具执行完成，但响应内容为空";
                    }
                } else {
                    return "工具执行完成，但响应格式异常";
                }
            }
            
            if (result.containsKey("error")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) result.get("error");
                String errorMessage = "MCP工具调用失败: " + error.get("message");
                log.error("MCP工具调用错误: server={}, tool={}, error={}", serverName, request.name(), error);
                throw new RuntimeException(errorMessage);
            }
            
            log.error("MCP工具调用响应格式错误: server={}, tool={}", serverName, request.name());
            throw new RuntimeException("MCP工具调用响应格式错误: 缺少result或error字段");
            
        } catch (Exception e) {
            log.error("MCP工具执行失败: server={}, tool={}", serverName, request.name(), e);
            throw new RuntimeException("MCP工具执行失败: " + e.getMessage(), e);
        }
    }


    /**
     * 获取指定服务器的工具规格列表
     */
    public List<ToolSpecification> getToolSpecifications(String serverName) {
        WebClient webClient = mcpClientMap.get(serverName);
        if (webClient == null) {
            return new ArrayList<>();
        }
        
        try {
            // 构建MCP工具列表请求
            Map<String, Object> mcpRequest = Map.of(
                "jsonrpc", "2.0",
                "id", System.currentTimeMillis(),
                "method", "tools/list",
                "params", Map.of()
            );
            
            // 发送请求
            String response = webClient.post()
                    .bodyValue(mcpRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                    .block();
            
            // 解析响应
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            
            if (result.containsKey("result")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultData = (Map<String, Object>) result.get("result");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tools = (List<Map<String, Object>>) resultData.get("tools");
                
                // 转换为ToolSpecification
                List<ToolSpecification> specifications = new ArrayList<>();
                for (Map<String, Object> tool : tools) {
                    ToolSpecification spec = buildToolSpecification(tool);
                    specifications.add(spec);
                }
                
                return specifications;
            }
            
            if (result.containsKey("error")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) result.get("error");
                log.error("获取工具规格失败: server={}, error={}", serverName, error.get("message"));
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("获取工具规格失败: server={}", serverName, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取所有可用的工具规格
     */
    public List<ToolSpecification> getAllToolSpecifications() {
        List<ToolSpecification> allSpecs = new ArrayList<>();
        
        for (String serverName : mcpClientMap.keySet()) {
            try {
                List<ToolSpecification> serverSpecs = getToolSpecifications(serverName);
                allSpecs.addAll(serverSpecs);
            } catch (Exception e) {
                log.warn("从服务器 {} 获取工具规格失败: {}", serverName, e.getMessage());
            }
        }
        
        return allSpecs;
    }
    
    /**
     * 检查工具是否存在于MCP服务器中（轻量级检查）
     */
    public boolean isMCPTool(String toolName) {
        for (String serverName : mcpClientMap.keySet()) {
            try {
                if (hasTool(serverName, toolName)) {
                    return true;
                }
            } catch (Exception e) {
                // 忽略检查错误，继续检查其他服务器
            }
        }
        return false;
    }
    
    /**
     * 检查指定服务器是否包含指定工具（轻量级检查）
     */
    private boolean hasTool(String serverName, String toolName) {
        WebClient webClient = mcpClientMap.get(serverName);
        if (webClient == null) {
            return false;
        }
        
        try {
            // 构建工具列表请求
            Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "tools/list"
            );
            
            // 发送请求并检查响应中是否包含指定工具
            String response = webClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                    .block();
            
            if (response == null || response.trim().isEmpty()) {
                return false;
            }
            
            // 简单检查响应中是否包含工具名称（避免完整解析）
            return response.contains("\"name\":\"" + toolName + "\"");
            
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查MCP服务器健康状态
     */
    public boolean isHealthy(String serverName) {
        WebClient webClient = mcpClientMap.get(serverName);
        if (webClient == null) {
            return false;
        }
        
        try {
            getToolSpecifications(serverName);
            return true;
        } catch (Exception e) {
            log.warn("MCP服务器健康检查失败: {}", serverName, e);
            return false;
        }
    }

    /**
     * 获取所有MCP服务器状态
     */
    public Map<String, Boolean> getAllServerStatus() {
        Map<String, Boolean> statusMap = new ConcurrentHashMap<>();
        
        for (String serverName : mcpClientMap.keySet()) {
            statusMap.put(serverName, isHealthy(serverName));
        }
        
        return statusMap;
    }

    /**
     * 构建工具规格
     * 解析MCP工具返回的数据并构造完整的ToolSpecification
     */
    private ToolSpecification buildToolSpecification(Map<String, Object> tool) {
        String name = (String) tool.get("name");
        String description = (String) tool.get("description");
        
        // 解析inputSchema
        JsonObjectSchema parameters = parseInputSchema(tool);
        
        // 直接使用MCP服务器提供的原始描述，不进行任何增强
        return ToolSpecification.builder()
                .name(name)
                .description(description)
                .parameters(parameters)
                .build();
    }
    

    /**
     * 解析MCP工具的inputSchema
     * 将MCP协议的JSON Schema转换为JsonObjectSchema
     */
    @SuppressWarnings("unchecked")
    private JsonObjectSchema parseInputSchema(Map<String, Object> tool) {
        try {
            Object inputSchemaObj = tool.get("inputSchema");
            if (inputSchemaObj == null) {
                return JsonObjectSchema.builder().build();
            }
            
            Map<String, Object> inputSchema = (Map<String, Object>) inputSchemaObj;
            String type = (String) inputSchema.get("type");
            
            if (!"object".equals(type)) {
                return JsonObjectSchema.builder().build();
            }
            
            // 解析properties
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            List<String> required = (List<String>) inputSchema.get("required");
            
            JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
            
            if (properties != null) {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    String propName = entry.getKey();
                    Map<String, Object> propSchema = (Map<String, Object>) entry.getValue();
                    
                    JsonSchemaElement propertySchema = parsePropertySchema(propName, propSchema);
                    if (propertySchema != null) {
                        schemaBuilder.addProperty(propName, propertySchema);
                    }
                }
            }
            
            // 设置required字段
            if (required != null && !required.isEmpty()) {
                schemaBuilder.required(required.toArray(new String[0]));
            }
            
            return schemaBuilder.build();
            
        } catch (Exception e) {
            log.error("解析工具 {} 的inputSchema失败: {}", tool.get("name"), e.getMessage(), e);
            return JsonObjectSchema.builder().build();
        }
    }

    /**
     * 解析单个属性的schema
     */
    @SuppressWarnings("unchecked")
    private JsonSchemaElement parsePropertySchema(String propName, Map<String, Object> propSchema) {
        try {
            String type = (String) propSchema.get("type");
            String description = (String) propSchema.get("description");
            
            if (type == null) {
                return JsonStringSchema.builder().description("未知类型").build();
            }
            
            switch (type) {
                case "string":
                    return JsonStringSchema.builder().description(description).build();
                    
                case "integer":
                    return JsonIntegerSchema.builder().description(description).build();
                    
                case "number":
                    return JsonNumberSchema.builder().description(description).build();
                    
                case "boolean":
                    return JsonBooleanSchema.builder().description(description).build();
                    
                case "array":
                    // 处理数组类型
                    return parseArraySchema(propSchema, description);
                    
                case "object":
                    // 处理对象类型
                    return parseObjectSchema(propSchema, description);
                    
                default:
                    return JsonStringSchema.builder().description(description).build();
            }
            
        } catch (Exception e) {
            log.error("解析属性 {} 的schema失败: {}", propName, e.getMessage(), e);
            return JsonStringSchema.builder().description("解析失败").build();
        }
    }
    
    /**
     * 解析数组类型的schema
     */
    @SuppressWarnings("unchecked")
    private JsonArraySchema parseArraySchema(Map<String, Object> propSchema, String description) {
        Object itemsObj = propSchema.get("items");
        if (itemsObj instanceof Map) {
            Map<String, Object> itemsSchema = (Map<String, Object>) itemsObj;
            String itemsType = (String) itemsSchema.get("type");
            
            if (itemsType == null) {
                // 如果没有type，默认为字符串
                return JsonArraySchema.builder()
                        .items(JsonStringSchema.builder().build())
                        .description(description)
                        .build();
            }
            
            // 根据items的类型递归解析
            JsonSchemaElement itemSchema;
            switch (itemsType) {
                case "string":
                    itemSchema = JsonStringSchema.builder()
                            .description((String) itemsSchema.get("description"))
                            .build();
                    break;
                    
                case "integer":
                    itemSchema = JsonIntegerSchema.builder()
                            .description((String) itemsSchema.get("description"))
                            .build();
                    break;
                    
                case "number":
                    itemSchema = JsonNumberSchema.builder()
                            .description((String) itemsSchema.get("description"))
                            .build();
                    break;
                    
                case "boolean":
                    itemSchema = JsonBooleanSchema.builder()
                            .description((String) itemsSchema.get("description"))
                            .build();
                    break;
                    
                case "object":
                    // 递归解析对象类型
                    itemSchema = parseObjectSchema(itemsSchema, (String) itemsSchema.get("description"));
                    break;
                    
                case "array":
                    // 递归解析嵌套数组
                    itemSchema = parseArraySchema(itemsSchema, (String) itemsSchema.get("description"));
                    break;
                    
                default:
                    itemSchema = JsonStringSchema.builder().build();
                    break;
            }
            
            return JsonArraySchema.builder()
                    .items(itemSchema)
                    .description(description)
                    .build();
        }
        
        // 默认返回字符串数组
        return JsonArraySchema.builder()
                .items(JsonStringSchema.builder().build())
                .description(description)
                .build();
    }
    
    /**
     * 解析对象类型的schema
     */
    @SuppressWarnings("unchecked")
    private JsonObjectSchema parseObjectSchema(Map<String, Object> propSchema, String description) {
        try {
            // 解析properties
            Map<String, Object> properties = (Map<String, Object>) propSchema.get("properties");
            List<String> required = (List<String>) propSchema.get("required");
            
            JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
            
            if (description != null) {
                schemaBuilder.description(description);
            }
            
            if (properties != null) {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    String propName = entry.getKey();
                    Map<String, Object> nestedPropSchema = (Map<String, Object>) entry.getValue();
                    
                    // 递归解析嵌套属性
                    JsonSchemaElement propertySchema = parsePropertySchema(propName, nestedPropSchema);
                    if (propertySchema != null) {
                        schemaBuilder.addProperty(propName, propertySchema);
                    }
                }
            }
            
            // 设置required字段
            if (required != null && !required.isEmpty()) {
                schemaBuilder.required(required.toArray(new String[0]));
            }
            
            return schemaBuilder.build();
            
        } catch (Exception e) {
            log.error("解析对象schema失败: {}", e.getMessage(), e);
            return JsonObjectSchema.builder().description(description).build();
        }
    }
    

}
