package com.xiaohongshu.codewiz.codewizagent.agentcore.session;

import com.xiaohongshu.codewiz.codewizagent.agentcore.auth.AuthServer;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.UserPrincipal;
import com.xiaohongshu.infra.rpc.ciuser.RpcBaseUserInfo;
import jakarta.annotation.Resource;
import java.security.Principal;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Component
public class UserHandshakeHandler extends DefaultHandshakeHandler {

    @Resource
    private AuthServer authServer;

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String userEmail = request.getHeaders().getFirst("user-id");
        String workspace = null;
        if (userEmail == null) {
            String rawQuery = request.getURI().getRawQuery();
            if (StringUtils.isNoneEmpty(rawQuery)) {
                String[] queryArray = rawQuery.split("&");
                for (String query : queryArray) {
                    if (query.contains("useremail=")) userEmail = getSplitArrayOne(query);
                    if (query.contains("workspace=")) workspace = getSplitArrayOne(query);
                }
            }
        }
        RpcBaseUserInfo baseUserInfo = authServer.authUser(userEmail);
        if (baseUserInfo == null) {
            throw new RuntimeException("用户未登录");
        }
        if (workspace == null) {
            throw new RuntimeException("workspace:工作空间不能为空");
        }
        attributes.put("user", baseUserInfo);
        attributes.put("workspace", workspace);
        return new UserPrincipal(baseUserInfo.getEmail()); // 将 user-id 绑定到 Principal
    }

    public String getSplitArrayOne(String query) {
        String[] split = query.split("=");
        return split.length == 2 ? split[1] : null;
    }
}