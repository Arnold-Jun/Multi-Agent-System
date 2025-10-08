package com.xiaohongshu.codewiz.codewizagent.agentcore.auth;

import com.xiaohongshu.codewiz.codewizagent.agentcore.auth.CiUserRpcClientService;
import com.xiaohongshu.infra.rpc.ciuser.RpcBaseUserInfo;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/9 17:25
 */
@Slf4j
@Component
public class AuthServer {

    @Resource
    private CiUserRpcClientService CiUserRpcClientService;

    /**
     * 用户鉴权，返回用户的email
     * @param userEmail
     * @return
     */
    public RpcBaseUserInfo authUser(String userEmail) {
        RpcBaseUserInfo rpcBaseUserInfo = CiUserRpcClientService.syncBaseUserOnly(userEmail);
        return rpcBaseUserInfo;
    }
}
