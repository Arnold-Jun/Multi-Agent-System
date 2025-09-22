package com.zhouruojun.dataanalysisagent.agent;

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
import java.util.List;
import java.util.Objects;

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

    private ChatRequest prepareRequest(List<ChatMessage> messages, String... args ) {
        var reqMessages = new ArrayList<ChatMessage>();

        String prompt = getPrompt();
        if (StringUtils.isEmpty(prompt)) {
            reqMessages.add(SystemMessage.from("You are a helpful assistant"));
        } else {
            reqMessages.add(SystemMessage.from(prompt));
        }
        reqMessages.addAll(messages);

        var parameters = ChatRequestParameters.builder()
                .toolSpecifications(tools)
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
}
