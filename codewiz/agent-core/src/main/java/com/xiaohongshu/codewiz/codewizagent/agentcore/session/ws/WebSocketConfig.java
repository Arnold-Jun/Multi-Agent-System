package com.xiaohongshu.codewiz.codewizagent.agentcore.session.ws;

import com.xiaohongshu.codewiz.codewizagent.agentcore.session.SessionChannelInterceptor;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.UserHandshakeHandler;
import jakarta.annotation.Resource;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Slf4j
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker // 启用 STOMP 消息代理
public class WebSocketConfig implements WebSocketConfigurer, WebSocketMessageBrokerConfigurer {

    @Resource
    private UserHandshakeHandler userHandshakeHandler;

    @Resource
    private SessionChannelInterceptor sessionChannelInterceptor;

    @Resource
    private NativeWebSocketHandler nativeWebSocketHandler;

    // ================= websocket 相关配置 ==========================

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(nativeWebSocketHandler, "/native/ws")
                .setHandshakeHandler(userHandshakeHandler)
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }


    // ================ stomp 相关配置 ==========================

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 定义客户端连接端点（支持 SockJS）
        registry.addEndpoint("/ws") // WebSocket 连接路径
                .setHandshakeHandler(userHandshakeHandler)
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 配置消息代理
        registry.enableSimpleBroker("/topic", "/queue"); // 客户端订阅目标前缀（广播和点对点）
        registry.setApplicationDestinationPrefixes("/agentapi"); // 服务端监听前缀（@MessageMapping 方法）
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(sessionChannelInterceptor);
    }
//
//    @Override
//    public void configureClientOutboundChannel(ChannelRegistration registration) {
//        registration.interceptors()
//    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> converters) {
        converters.add(new StringMessageConverter());
//        converters.add(new ByteArrayMessageConverter());
//        converters.add(new MappingJackson2MessageConverter());
        return false;
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(1024 * 1024);      // 单条消息最大 1MB
        registry.setSendBufferSizeLimit(1024 * 1024 * 5); // 发送缓冲区最大 5MB
        registry.setSendTimeLimit(20 * 1000);            // 发送超时时间 20 秒
    }

}
