package com.zhouruojun.travelingagent.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.Builder;
import lombok.Singular;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Represents an agent that can process chat messages and execute actions using specified tools.
 * Copied from agent-core to maintain consistency
 */
@Builder
public class BaseAgent {

    protected final ChatLanguageModel chatLanguageModel;
    protected final StreamingChatLanguageModel streamingChatLanguageModel;
    @Singular protected final List<ToolSpecification> tools;
    protected String agentName;
    protected String prompt;

    /**
     * Checks if the agent is currently streaming.
     *
     * @return true if the agent is streaming, false otherwise.
     */
    public boolean isStreaming() {
        return streamingChatLanguageModel != null;
    }

    private ChatRequest prepareRequest(List<ChatMessage> messages ) {
        return prepareRequest(messages, (String[]) null);
    }

    /**
     * Default prompt for the agent.
     * Need to extend this method to provide a custom prompt.
     * @return
     */
    protected String getPrompt() {
        if (prompt != null && !prompt.isEmpty()) {
            return prompt;
        }
        return "You are a helpful assistant";
    }

    /**
     * Get prompt with parameters
     */
    protected String getPrompt(Map<String, Object> map) {
        // 简单的参数替换实现
        String basePrompt = getPrompt();
        if (map != null && !map.isEmpty()) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                basePrompt = basePrompt.replace(placeholder, value);
            }
        }
        return basePrompt;
    }

    private ChatRequest prepareRequest(List<ChatMessage> messages, String... args ) {
        return prepareRequest(messages, null, null, args);
    }

    private ChatRequest prepareRequest(List<ChatMessage> messages, Supplier<Map<String, Object>> supplier, List<ToolSpecification> toolSpecifications, String... args) {
        var reqMessages = new ArrayList<ChatMessage>();

        String prompt;
        if (supplier != null) {
            prompt = getPrompt(supplier.get());
        } else {
            prompt = getPrompt();
        }
        
        if (StringUtils.isEmpty(prompt)) {
            reqMessages.add(SystemMessage.from("You are a helpful assistant"));
        } else {
            reqMessages.add(SystemMessage.from(prompt));
        }
        reqMessages.addAll(messages);

        // 使用传入的工具规格，如果没有则使用默认的
        List<ToolSpecification> finalToolSpecs = toolSpecifications != null ? toolSpecifications : tools;
        
        var parameters = ChatRequestParameters.builder()
                .toolSpecifications(finalToolSpecs)
                .build();
        return ChatRequest.builder()
                .messages( reqMessages )
                .parameters(parameters)
                .build();
    }

    /**
     * Executes the agent's action based on the input and intermediate steps, using a streaming response handler.
     *
     * @param messages the messages to process.
     * @param handler the handler for streaming responses.
     */
    public void execute(List<ChatMessage> messages, StreamingChatResponseHandler handler) {
        Objects.requireNonNull(streamingChatLanguageModel, "streamingChatLanguageModel is required!");

        streamingChatLanguageModel.chat(prepareRequest(messages), handler);
    }

    /**
     * Executes the agent's action based on the input and intermediate steps, using a streaming response handler.
     *
     * @param messages the messages to process.
     * @param handler the handler for streaming responses.
     */
    public void execute(List<ChatMessage> messages, StreamingChatResponseHandler handler,  String... args ) {
        Objects.requireNonNull(streamingChatLanguageModel, "streamingChatLanguageModel is required!");

        streamingChatLanguageModel.chat(prepareRequest(messages, args), handler);
    }

    /**
     * Executes the agent's action based on the input and intermediate steps, returning a response.
     *
     * @param messages the messages to process.
     * @return a response containing the generated AI message.
     */
    public ChatResponse execute(List<ChatMessage> messages ) {
        Objects.requireNonNull(chatLanguageModel, "chatLanguageModel is required!");

       return chatLanguageModel.chat(prepareRequest(messages));
    }

    /**
     * Executes the agent's action based on the input and intermediate steps, returning a response.
     * With the ability to pass in arguments to replace placeholders in the prompt.
     * @param messages the messages to process.
     * @param args the arguments to replace placeholders in the prompt.
     *             roles are defined in the prompt as {{$0}}, {{$1}}, etc.
     * @return a response containing the generated AI message.
     */
    public ChatResponse execute(List<ChatMessage> messages, String... args ) {
        Objects.requireNonNull(chatLanguageModel, "chatLanguageModel is required!");
        return chatLanguageModel.chat(prepareRequest(messages, args));
    }

    /**
     * Executes the agent's action based on the input and intermediate steps, returning a response.
     * With the ability to pass in arguments to replace placeholders in the prompt and tool specifications.
     * @param messages the messages to process.
     * @param supplier the supplier for prompt parameters.
     * @param toolSpecifications the tool specifications to use.
     * @param args the arguments to replace placeholders in the prompt.
     * @return a response containing the generated AI message.
     */
    public ChatResponse execute(List<ChatMessage> messages, Supplier<Map<String, Object>> supplier, List<ToolSpecification> toolSpecifications, String... args) {
        Objects.requireNonNull(chatLanguageModel, "chatLanguageModel is required!");
        return chatLanguageModel.chat(prepareRequest(messages, supplier, toolSpecifications, args));
    }
}

