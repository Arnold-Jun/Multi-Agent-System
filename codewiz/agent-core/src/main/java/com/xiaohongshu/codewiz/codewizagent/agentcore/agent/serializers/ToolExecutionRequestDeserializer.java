package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.serializers;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.io.IOException;

class ToolExecutionRequestDeserializer extends JsonDeserializer<ToolExecutionRequest> {
    ToolExecutionRequestDeserializer() {
    }

    public ToolExecutionRequest deserialize(JsonParser parser, DeserializationContext ctx) throws IOException, JacksonException {
        JsonNode node = (JsonNode)parser.getCodec().readTree(parser);
        return ToolExecutionRequest.builder().id(node.get("id").asText()).name(node.get("name").asText()).arguments(node.get("arguments").asText()).build();
    }
}