package com.xiaohongshu.codewiz.codewizagent.agentcore.a2a;

import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.a2a.notification.mvc.autoconfiguration.A2aNotificationProperties;
import com.xiaohongshu.codewiz.a2acore.core.PushNotificationAuth;
import com.xiaohongshu.codewiz.a2acore.spec.DataPart;
import com.xiaohongshu.codewiz.a2acore.spec.Task;
import com.xiaohongshu.codewiz.a2acore.spec.message.util.Util;
import com.xiaohongshu.codewiz.a2aspringmvc.WebMvcNotificationAdapter;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalToolMessageRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.SessionManager;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * <p>
 * notification 通知的消息，基本上都是用于展示text的文本信息
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/14 10:37
 */
@Slf4j
@Component
public class A2aNotificationListener extends WebMvcNotificationAdapter {
    protected final ScheduledThreadPoolExecutor scheduler;

    public A2aNotificationListener(@Autowired A2aNotificationProperties a2aNotificationProperties) {
        super(a2aNotificationProperties.getEndpoint(), a2aNotificationProperties.getJwksUrls());

        // auto reloadJwks when Agent restart
        scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.scheduleAtFixedRate(() -> {
            if (verifyFailCount.get() != 0) {
                this.reloadJwks();
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public ServerResponse handleNotification(ServerRequest request) {
        List<String> header = request.headers().header(PushNotificationAuth.AUTH_HEADER);
        String authorization = header.isEmpty() ? "" : header.get(0);
        try {
            Task data = request.body(Task.class);
            boolean verified = this.auth.verifyPushNotification(authorization, data);
            if (!verified) {
                verifyFailCount.incrementAndGet();
                log.info("push notification verification failed, authorization: {}, data: {}", authorization, Util.toJson(data));
                return ServerResponse.badRequest().body("push notification verification failed, authorization: " + authorization);
            }
            if (data.getStatus() == null || data.getStatus().getMessage() == null) {
                log.error("push notification received, but status or message is null, data:{}", JSONObject.toJSONString(data));
                return ServerResponse.status(HttpStatus.BAD_REQUEST).body("push notification received, but status or message is null");
            }
            log.info("push notification received, authorization: {}, data: {}", authorization, Util.toJson(data));
            // 不要历史
            data.setHistory(null);
            data.setArtifacts(data.getArtifacts().stream().filter(Objects::nonNull).toList());
            // 直接将消息发送给上层，这里，也许需要封装一层能力类型与数据分类
            if ("tool_call".equals(data.getStatus().getMessage().getMetadata().getOrDefault("method", ""))) {
                // tool_call 代表着直接执行local的mcp工具
                DataPart dataPart = (DataPart) data.getStatus().getMessage().getParts().getLast();
                Map<String, Object> dataPartData = dataPart.getData();
                Map<String, Object> wrapperData = new HashMap<>();
                wrapperData.put("data", dataPartData);
                wrapperData.put("sessionId", data.getSessionId());
                SessionManager.getInstance().sendNativeActionMessage(data.getSessionId(), JSONObject.toJSONString(wrapperData));
            } else {
                SessionManager.getInstance().sendNativeObserverMessage(data.getSessionId(), JSONObject.toJSONString(data));
            }
            log.info("receive notification data: {}", JSONObject.toJSONString(data));
        } catch (Exception e) {
            log.error("error verifying push notification, authorization: {}, error: {}", authorization, e.getMessage(), e);
            verifyFailCount.incrementAndGet();
            return ServerResponse.badRequest().body(e.getMessage());
        }
        return ServerResponse.ok().build();
    }
}
