package com.zhouruojun.agentcore.core;

import com.zhouruojun.agentcore.spec.UpdateEvent;
import com.zhouruojun.agentcore.spec.message.CancelTaskRequest;
import com.zhouruojun.agentcore.spec.message.CancelTaskResponse;
import com.zhouruojun.agentcore.spec.message.GetTaskPushNotificationRequest;
import com.zhouruojun.agentcore.spec.message.GetTaskPushNotificationResponse;
import com.zhouruojun.agentcore.spec.message.GetTaskRequest;
import com.zhouruojun.agentcore.spec.message.GetTaskResponse;
import com.zhouruojun.agentcore.spec.message.JsonRpcResponse;
import com.zhouruojun.agentcore.spec.message.SendTaskRequest;
import com.zhouruojun.agentcore.spec.message.SendTaskResponse;
import com.zhouruojun.agentcore.spec.message.SendTaskStreamingRequest;
import com.zhouruojun.agentcore.spec.message.SetTaskPushNotificationRequest;
import com.zhouruojun.agentcore.spec.message.SetTaskPushNotificationResponse;
import com.zhouruojun.agentcore.spec.message.TaskResubscriptionRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 任务管理�? * 依靠 <a href="https://github.com/google/A2A/blob/main/docs/specification.md#7-protocol-rpc-methods">点击跳转</a>
 * 定义对应接口
 * </p>
 *
 */
public interface TaskManager {

    Mono<GetTaskResponse> onGetTask(GetTaskRequest request);

    Mono<SendTaskResponse> onSendTask(SendTaskRequest request);

    Mono<? extends JsonRpcResponse<?>> onSendTaskSubscribe(SendTaskStreamingRequest request);

    Mono<CancelTaskResponse> onCancelTask(CancelTaskRequest request);

    Mono<GetTaskPushNotificationResponse> onGetTaskPushNotification(GetTaskPushNotificationRequest request);

    Mono<SetTaskPushNotificationResponse> onSetTaskPushNotification(SetTaskPushNotificationRequest request);

    Mono<? extends JsonRpcResponse<?>> onResubscribeTask(TaskResubscriptionRequest request);

    Flux<UpdateEvent> dequeueEvent(String taskId);

    Mono<Void> closeGracefully();
}
