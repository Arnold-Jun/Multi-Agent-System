package com.zhouruojun.jobsearchagent.a2a;

import com.zhouruojun.a2acore.spec.message.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * A2A协议控制器
 * 处理来自其他智能体的A2A协议请求
 */
@Slf4j
@RestController
@RequestMapping("/a2a")
public class A2AProtocolController {

    @Autowired
    private JobSearchTaskManager taskManager;

    /**
     * 处理A2A任务发送请求
     */
    @PostMapping("/task")
    public Mono<SendTaskResponse> sendTask(@RequestBody SendTaskRequest request) {
        log.info("Received A2A task request: {}", request.getId());
        return taskManager.onSendTask(request);
    }

    /**
     * 处理A2A流式任务订阅请求
     */
    @PostMapping(value = "/task/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<? extends JsonRpcResponse<?>> sendTaskSubscribe(@RequestBody SendTaskStreamingRequest request) {
        log.info("Received A2A streaming task request: {}", request.getId());
        return taskManager.onSendTaskSubscribe(request);
    }

    /**
     * 处理A2A任务查询请求
     */
    @PostMapping("/task/get")
    public Mono<GetTaskResponse> getTask(@RequestBody GetTaskRequest request) {
        log.info("Received A2A task query request: {}", request.getId());
        return taskManager.onGetTask(request);
    }

    /**
     * 处理A2A任务取消请求
     */
    @PostMapping("/task/cancel")
    public Mono<CancelTaskResponse> cancelTask(@RequestBody CancelTaskRequest request) {
        log.info("Received A2A task cancel request: {}", request.getId());
        return taskManager.onCancelTask(request);
    }

    /**
     * 处理A2A任务重新订阅请求
     */
    @PostMapping(value = "/task/resubscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<? extends JsonRpcResponse<?>> resubscribeTask(@RequestBody TaskResubscriptionRequest request) {
        log.info("Received A2A task resubscribe request: {}", request.getId());
        return taskManager.onResubscribeTask(request);
    }
}
