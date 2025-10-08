package com.zhouruojun.agentcore.a2a;

import com.zhouruojun.a2a.notification.mvc.autoconfiguration.A2aNotificationProperties;
// import com.zhouruojun.a2aspringmvc.WebMvcNotificationAdapter; // 暂时注释，等待依赖
import com.zhouruojun.agentcore.session.SessionManager;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A2A通知监听器
 * 处理来自其他Agent的Push Notification
 */
@Slf4j
@Component
public class A2aNotificationListener { // extends WebMvcNotificationAdapter { // 暂时注释，等待依赖
    
    protected final ScheduledThreadPoolExecutor scheduler;
    
    @Autowired
    @SuppressWarnings("unused") // 暂时未使用，等待依赖完善
    private SessionManager sessionManager;

    public A2aNotificationListener(@Autowired A2aNotificationProperties a2aNotificationProperties) {
        // super(a2aNotificationProperties.getEndpoint(), a2aNotificationProperties.getJwksUrls()); // 暂时注释

        // 自动重新加载JWKS当Agent重启时
        scheduler = new ScheduledThreadPoolExecutor(1);
        // 暂时注释掉JWKS相关功能
        // scheduler.scheduleAtFixedRate(() -> {
        //     if (verifyFailCount.get() != 0) {
        //         this.reloadJwks();
        //     }
        // }, 1, 1, TimeUnit.MINUTES);
    }

    // @Override // 暂时注释，等待依赖
    public void handleNotification(Object request) { // 简化方法签名
        try {
            log.info("A2A notification received: {}", request);
            // TODO: 实现完整的通知处理逻辑，等待依赖完善
        } catch (Exception e) {
            log.error("Error handling A2A notification", e);
        }
    }
}

