package com.xiaohongshu.codewiz.codewizagent.cragent.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Singular;
import org.apache.commons.lang3.StringUtils;


/**
 * Represents an agent that can process chat messages and execute actions using specified tools.
 */
@Builder
public class BaseAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    @Singular private final List<ToolSpecification> tools;
    private String prompt;

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

    private ChatRequest prepareRequest(List<ChatMessage> messages, String... args ) {
        var reqMessages = new ArrayList<ChatMessage>();
        if (StringUtils.isEmpty(prompt)) {
            reqMessages.add(SystemMessage.from("You are a helpful assistant"));
        } else {
            String replacePrompt = null;
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    replacePrompt = prompt.replaceAll("\\{\\{\\$" + i + "}}", args[i]);
                }
            } else {
                replacePrompt = prompt;
            }
            reqMessages.add(SystemMessage.from(replacePrompt));
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
