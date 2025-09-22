package com.zhouruojun.agentcore.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouruojun.agentcore.spec.AgentCard;
import com.zhouruojun.agentcore.spec.PushNotificationConfig;
import com.zhouruojun.agentcore.spec.TaskIdParams;
import com.zhouruojun.agentcore.spec.TaskQueryParams;
import com.zhouruojun.agentcore.spec.TaskSendParams;
import com.zhouruojun.agentcore.client.sse.SseEventHandler;
import com.zhouruojun.agentcore.spec.error.JSONParseError;
import com.zhouruojun.agentcore.spec.message.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Getter
@Slf4j
public class A2AClient {
    private final AgentCard agentCard;
    private final PushNotificationConfig pushNotificationConfig;
    @Getter(AccessLevel.PROTECTED)
    private final transient WebClient webClient;
    @Getter(AccessLevel.PROTECTED)
    private final transient ObjectMapper objectMapper = new ObjectMapper();
    private List<SseEventHandler> sseEventHandlers;

    private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

    public A2AClient(AgentCard agentCard, List<SseEventHandler> sseEventHandlers, WebClient webClient) {
        this(agentCard, sseEventHandlers, null, webClient);
    }

    public A2AClient(AgentCard agentCard, List<SseEventHandler> sseEventHandlers, PushNotificationConfig pushNotificationConfig, WebClient webClient) {
        this.agentCard = agentCard;
        this.pushNotificationConfig = pushNotificationConfig;
        this.sseEventHandlers = sseEventHandlers;
        this.webClient = webClient;
    }


    public SendTaskResponse sendTask(TaskSendParams params) {
        SendTaskRequest request = new SendTaskRequest(params);
        Mono<JsonRpcResponse> responseMono = this.doSendRequest(request);
        return responseMono.map(r -> {
            TypeReference<SendTaskResponse> ref = new TypeReference<SendTaskResponse>() {
            };
            return objectMapper.convertValue(r, ref);
        }).block();
    }

    public GetTaskResponse getTask(TaskQueryParams params) {
        GetTaskRequest request = new GetTaskRequest(params);
        Mono<JsonRpcResponse> responseMono = this.doSendRequest(request);
        return responseMono.map(r -> {
            TypeReference<GetTaskResponse> ref = new TypeReference<GetTaskResponse>() {
            };
            return objectMapper.convertValue(r, ref);
        }).block();
    }

    public CancelTaskResponse cancelTask(TaskIdParams params) {
        CancelTaskRequest request = new CancelTaskRequest(params);
        Mono<JsonRpcResponse> responseMono = this.doSendRequest(request);
        return responseMono.map(r -> {
            TypeReference<CancelTaskResponse> ref = new TypeReference<CancelTaskResponse>() {
            };
            return objectMapper.convertValue(r, ref);
        }).block();
    }

    public Flux<SendTaskStreamingResponse> sendTaskSubscribe(TaskSendParams params) {
        SendTaskStreamingRequest request = new SendTaskStreamingRequest(params);
        return this.doSendRequestForSse(request);
    }

    public Flux<SendTaskStreamingResponse> sendTaskResubscribe(TaskIdParams params) {
        TaskResubscriptionRequest request = new TaskResubscriptionRequest(params);
        return this.doSendRequestForSse(request);
    }

    private Flux<SendTaskStreamingResponse> doSendRequestForSse(JsonRpcRequest request) {
        try {
            return webClient.post()
                    .header("Content-Type", "application/json")
                    .header("Content-Encoding", StandardCharsets.UTF_8.name())
                    .body(Mono.just(request), JsonRpcRequest.class)
                    .retrieve()
                    .bodyToFlux(String.class)
                    // cancels the flux stream after the "[DONE]" is received.
                    .takeUntil(SSE_DONE_PREDICATE)
                    // filters out the "[DONE]" message.
                    .filter(SSE_DONE_PREDICATE.negate())
                    .map(this::objectMapperReadValue)
                    .doOnNext(next -> {
                        if (sseEventHandlers != null) {
                            for (SseEventHandler sseEventHandler : sseEventHandlers) {
                                try {
                                    log.info("doSendRequestForSse next:{}", objectMapper.writeValueAsString(next));
                                    sseEventHandler.onEvent(this, next);
                                } catch (JsonProcessingException e) {
                                    log.error("ERROR objectMapperWriteValueAsString:{}", e.getMessage(), e);
                                    sseEventHandler.onError(this, e);
                                }
                            }
                        }
                    })
                    .doOnError(e -> {
                        if (sseEventHandlers != null) {
                            sseEventHandlers.forEach(sseEventHandler -> sseEventHandler.onError(this, e));
                        }
                    })
                    ;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Mono<JsonRpcResponse> doSendRequest(JsonRpcRequest request) {
        try {
            return webClient.post()
                    .header("Content-Type", "application/json")
                    .header("Content-Encoding", StandardCharsets.UTF_8.name())
                    .body(Mono.just(request), JsonRpcRequest.class)
                    .retrieve()
                    .bodyToMono(JsonRpcResponse.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SendTaskStreamingResponse objectMapperReadValue(String content) {
        try {
            return objectMapper.readValue(content, SendTaskStreamingResponse.class);
        } catch (JsonProcessingException e) {
            log.error("ERROR objectMapperReadValue:{}", e.getMessage(), e);
            return new SendTaskStreamingResponse("", new JSONParseError());
        }
    }
}
