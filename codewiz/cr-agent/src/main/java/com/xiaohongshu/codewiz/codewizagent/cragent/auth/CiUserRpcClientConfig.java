package com.xiaohongshu.codewiz.codewizagent.cragent.auth;

import com.xiaohongshu.infra.rpc.ciuser.CiUserRemoteService;
import com.xiaohongshu.infra.rpc.client.ClientBuilder;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CiUserRpcClientConfig {

    @Value("${rpc.ciuser.name}")
    private String serviceName;

    @Value("${rpc.ciuser.rpcTimeout}")
    private int rpcTimeout;


    @Bean("ciUserRpcClient")
    public CiUserRemoteService.Iface ciUserRpcClient() {
        return ClientBuilder.create(CiUserRemoteService.Iface.class,
                serviceName).withTimeout(Duration.ofMillis(rpcTimeout)).buildStub();
    }
}
