package com.zhouruojun.a2acore.core;

import com.zhouruojun.a2acore.spec.UpdateEvent;
import com.zhouruojun.a2acore.spec.message.CancelTaskRequest;
import com.zhouruojun.a2acore.spec.message.CancelTaskResponse;
import com.zhouruojun.a2acore.spec.message.GetTaskPushNotificationRequest;
import com.zhouruojun.a2acore.spec.message.GetTaskPushNotificationResponse;
import com.zhouruojun.a2acore.spec.message.GetTaskRequest;
import com.zhouruojun.a2acore.spec.message.GetTaskResponse;
import com.zhouruojun.a2acore.spec.message.JsonRpcResponse;
import com.zhouruojun.a2acore.spec.message.SendTaskRequest;
import com.zhouruojun.a2acore.spec.message.SendTaskResponse;
import com.zhouruojun.a2acore.spec.message.SendTaskStreamingRequest;
import com.zhouruojun.a2acore.spec.message.SetTaskPushNotificationRequest;
import com.zhouruojun.a2acore.spec.message.SetTaskPushNotificationResponse;
import com.zhouruojun.a2acore.spec.message.TaskResubscriptionRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
<a href="https://github.com/google/A2A/blob/main/docs/specification.md#7-protocol-rpc-methods"></a>
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


