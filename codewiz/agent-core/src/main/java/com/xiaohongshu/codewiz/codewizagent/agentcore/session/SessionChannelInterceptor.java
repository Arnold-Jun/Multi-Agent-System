package com.xiaohongshu.codewiz.codewizagent.agentcore.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * <p>
 * Session的拦截器
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/2 15:03
 */
@Slf4j
@Component
public class SessionChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        log.info("SessionChannelInterceptor preSend, message length:{}");
        return ChannelInterceptor.super.preSend(message, channel);
    }

    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        ChannelInterceptor.super.postSend(message, channel, sent);
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        ChannelInterceptor.super.afterSendCompletion(message, channel, sent, ex);
    }
}
