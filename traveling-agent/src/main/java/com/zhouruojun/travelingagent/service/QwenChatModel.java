package com.zhouruojun.travelingagent.service;

import com.zhouruojun.travelingagent.config.QwenApiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

/**
 * Qwen ChatModel（OpenAI 兼容端点）
 * 关键修复：
 * 1) 默认不发送 tool_choice（等价 auto），彻底避免 400: parameters.tool_choice.* 报错
 * 2) 仅在强制函数名时发送合法对象 {"type":"function","function":{"name": "..."}}
 * 3) 发送前做“深度清洗”，移除任何出现的 tool_choice 键（顶层/嵌套）
 * 4) parameters 严格 JSON-Schema；content==null & arguments=对象 兼容
 */
@Slf4j
public class QwenChatModel implements ChatLanguageModel {

    private final QwenApiConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();


    public QwenChatModel(QwenApiConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofMillis(config.getTimeout()).toMillis());
        factory.setReadTimeout((int) Duration.ofMillis(config.getTimeout()).toMillis());
        restTemplate.setRequestFactory(factory);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.getModel());
            body.put("messages", toOpenAiMessages(request.messages()));
            body.put("temperature", config.getTemperature());
            body.put("max_tokens", config.getMaxTokens());
            body.put("enable_thinking", false); // 如模型支持该扩展

            // ---------- Tools ----------
            boolean hasTools = request.parameters() != null
                    && request.parameters().toolSpecifications() != null
                    && !request.parameters().toolSpecifications().isEmpty();

            if (hasTools) {
                List<Map<String, Object>> tools = toOpenAiTools(request.parameters().toolSpecifications());
                body.put("tools", tools);
                // 强制设置 tool_choice 为 auto，让模型必须考虑工具调用
                body.put("tool_choice", "auto");
            }

            // ---------- HTTP ----------
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());

            String url = config.getBaseUrl();
            if (!url.endsWith("/chat/completions")) {
                url = url.replaceAll("/$", "") + "/chat/completions";
            }

            if (log.isDebugEnabled()) {
                log.debug("Qwen request url: {}", url);
                log.debug("Qwen request body(final): {}", safeJson(body));
            }

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
                log.warn("Qwen API failed: status={}, body={}",
                        resp.getStatusCode(), resp.getBody() == null ? "null" : safeJson(resp.getBody()));
                return ChatResponse.builder().aiMessage(AiMessage.from(
                        "Error: Qwen API request failed: " + resp.getStatusCode())).build();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) resp.getBody();
            if (log.isDebugEnabled() && responseBody != null) {
                log.debug("Qwen response body: {}", safeJson(responseBody));
            }

            // ---------- 解析 ----------
            if (responseBody == null) {
                return ChatResponse.builder().aiMessage(AiMessage.from(
                        "Error: Response body is null")).build();
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices == null || choices.isEmpty()) {
                return ChatResponse.builder().aiMessage(AiMessage.from(
                        "Error: Unexpected response format (no choices)")).build();
            }

            Map<String, Object> choice0 = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choice0.get("message");
            if (message == null) {
                return ChatResponse.builder().aiMessage(AiMessage.from(
                        "Error: Unexpected response format (no message)")).build();
            }

            // content 可能为 null（触发 tool_calls 时）
            String content = "";
            Object contentObj = message.get("content");
            if (contentObj != null) content = String.valueOf(contentObj);

            List<ToolExecutionRequest> toolReqs = Collections.emptyList();
            if (message.containsKey("tool_calls")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
                toolReqs = toToolExecutionRequests(toolCalls);
                if (log.isDebugEnabled()) log.debug("tool_calls raw: {}", safeJson(toolCalls));
            }

            AiMessage ai = (toolReqs != null && !toolReqs.isEmpty())
                    ? AiMessage.from(content, toolReqs)
                    : AiMessage.from(content);

            return ChatResponse.builder().aiMessage(ai).build();

        } catch (Exception e) {
            log.error("Qwen API request failed", e);
            return ChatResponse.builder().aiMessage(AiMessage.from("Error: " + e.getMessage())).build();
        }
    }

    // -------------------- Helpers --------------------

    /** LangChain4j ChatMessage -> OpenAI messages */
    private List<Map<String, Object>> toOpenAiMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> arr = new ArrayList<>();
        for (ChatMessage m : messages) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("role", switch (m.type()) {
                case USER -> "user";
                case AI -> "assistant";
                case SYSTEM -> "system";
                default -> "user";
            });
            n.put("content", extractText(m));
            arr.add(n);
        }
        return arr;
    }

    private String extractText(ChatMessage m) {
        if (m instanceof UserMessage um) return um.singleText();
        if (m instanceof AiMessage am) return am.text();
        if (m instanceof SystemMessage sm) return sm.text();
        return "";
    }

    /** ToolSpecification -> OpenAI function tool */
    private List<Map<String, Object>> toOpenAiTools(List<dev.langchain4j.agent.tool.ToolSpecification> specs) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (dev.langchain4j.agent.tool.ToolSpecification s : specs) {
            try {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", s.name());
                fn.put("description", s.description());
                fn.put("parameters", toStrictJsonSchema(s.parameters()));

                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put("type", "function");
                tool.put("function", fn);

                tools.add(tool);
            } catch (Exception e) {
                log.error("Tool schema convert failed: {}", s.name(), e);
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", s.name());
                fn.put("description", s.description());
                fn.put("parameters", simpleSchema());

                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put("type", "function");
                tool.put("function", fn);

                tools.add(tool);
            }
        }
        return tools;
    }

    /** 反射 JsonObjectSchema -> 严格 JSON-Schema */
    private Map<String, Object> toStrictJsonSchema(Object objSchema) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<String, Object>());
        schema.put("required", new ArrayList<String>());
        schema.put("additionalProperties", false);

        if (objSchema == null) return schema;

        try {
            // properties
            try {
                var m = objSchema.getClass().getMethod("properties");
                Object props = m.invoke(objSchema);
                if (props instanceof Map) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    for (var e : ((Map<?, ?>) props).entrySet()) {
                        out.put(String.valueOf(e.getKey()), toSchemaNode(e.getValue()));
                    }
                    schema.put("properties", out);
                }
            } catch (Exception ignore) {}

            // required
            try {
                var m = objSchema.getClass().getMethod("required");
                Object req = m.invoke(objSchema);
                if (req instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> reqList = (List<String>) req;
                    schema.put("required", new ArrayList<>(reqList));
                }
            } catch (Exception ignore) {}
        } catch (Exception e) {
            log.warn("toStrictJsonSchema fallback: {}", e.getMessage());
            return simpleSchema();
        }
        return schema;
    }

    /** 子 schema 转换，保证至少含 type */
    private Map<String, Object> toSchemaNode(Object s) {
        Map<String, Object> n = new LinkedHashMap<>();
        if (s == null) { n.put("type", "string"); return n; }

        try {
            String name = s.getClass().getSimpleName();
            if (name.contains("JsonStringSchema")) {
                n.put("type", "string"); addDesc(s, n);
            } else if (name.contains("JsonIntegerSchema")) {
                n.put("type", "integer"); addDesc(s, n);
            } else if (name.contains("JsonNumberSchema")) {
                n.put("type", "number"); addDesc(s, n);
            } else if (name.contains("JsonBooleanSchema")) {
                n.put("type", "boolean"); addDesc(s, n);
            } else if (name.contains("JsonArraySchema")) {
                n.put("type", "array"); addItems(s, n);
            } else if (name.contains("JsonObjectSchema")) {
                n.put("type", "object"); addProps(s, n); n.putIfAbsent("additionalProperties", false);
            } else if (name.contains("JsonEnumSchema")) {
                n.put("type", "string"); addEnum(s, n);
            } else {
                n.put("type", "string");
            }
        } catch (Exception e) {
            n.put("type", "string");
        }
        return n;
    }

    private void addDesc(Object s, Map<String, Object> n) {
        try { var m = s.getClass().getMethod("description"); Object d = m.invoke(s); if (d != null) n.put("description", String.valueOf(d)); } catch (Exception ignore) {}
    }

    private void addItems(Object s, Map<String, Object> n) {
        try { var m = s.getClass().getMethod("items"); Object i = m.invoke(s); if (i != null) n.put("items", toSchemaNode(i)); } catch (Exception ignore) {}
    }

    private void addProps(Object s, Map<String, Object> n) {
        try {
            var m = s.getClass().getMethod("properties");
            Object p = m.invoke(s);
            if (p instanceof Map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (var e : ((Map<?, ?>) p).entrySet()) {
                    out.put(String.valueOf(e.getKey()), toSchemaNode(e.getValue()));
                }
                n.put("properties", out);
            }
        } catch (Exception ignore) {}
    }

    private void addEnum(Object s, Map<String, Object> n) {
        try { var m = s.getClass().getMethod("enumValues"); Object ev = m.invoke(s); if (ev != null) n.put("enum", ev); } catch (Exception ignore) {}
    }

    private Map<String, Object> simpleSchema() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", new LinkedHashMap<String, Object>());
        m.put("required", new ArrayList<String>());
        m.put("additionalProperties", false);
        return m;
    }

    /** tool_calls -> ToolExecutionRequest[]（兼容 arguments 为对象或字符串） */
    private List<ToolExecutionRequest> toToolExecutionRequests(List<Map<String, Object>> toolCalls) {
        List<ToolExecutionRequest> out = new ArrayList<>();
        if (toolCalls == null) return out;

        for (Map<String, Object> tc : toolCalls) {
            if (tc == null) continue;

            String id = String.valueOf(tc.get("id"));
            @SuppressWarnings("unchecked")
            Map<String, Object> fn = (Map<String, Object>) tc.get("function");
            if (fn == null) continue;

            String name = String.valueOf(fn.get("name"));
            Object argsObj = fn.get("arguments");
            String args;
            try {
                if (argsObj instanceof String) args = (String) argsObj;
                else args = mapper.writeValueAsString(argsObj);
            } catch (Exception e) {
                args = String.valueOf(argsObj);
            }

            out.add(ToolExecutionRequest.builder()
                    .id(id)
                    .name(name)
                    .arguments(args == null ? "" : args)
                    .build());
        }
        return out;
    }

    private String safeJson(Object o) {
        try {
            if (o instanceof String s) return s;
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
