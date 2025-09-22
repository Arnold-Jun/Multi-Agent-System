package com.zhouruojun.dataanalysisagent.agent.serializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.io.IOException;

/**
 * ToolExecutionRequest反序列化器
 * 用于JSONStateSerializer中反序列化ToolExecutionRequest对象
 */
public class ToolExecutionRequestDeserializer extends JsonDeserializer<ToolExecutionRequest> {

    @Override
    public ToolExecutionRequest deserialize(JsonParser p, DeserializationContext ctxt) 
            throws IOException, JsonProcessingException {
        
        JsonNode node = p.getCodec().readTree(p);
        
        String id = node.get("id").asText();
        String name = node.get("name").asText();
        
        // 解析arguments
        JsonNode argumentsNode = node.get("arguments");
        String arguments = argumentsNode != null ? argumentsNode.toString() : "{}";
        
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments(arguments)
                .build();
    }
}
