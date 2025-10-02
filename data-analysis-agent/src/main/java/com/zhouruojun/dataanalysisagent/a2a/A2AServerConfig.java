package com.zhouruojun.dataanalysisagent.a2a;

import com.zhouruojun.a2acore.core.server.A2AServer;
import com.zhouruojun.a2acore.spec.AgentCard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;

/**
 * A2A服务器配置
 * 为数据分析Agent提供A2A协议服务
 *
 */
@Configuration
public class A2AServerConfig {

    @Resource
    private AgentCard agentCard;

    @Bean
    public A2AServer a2aServer() {
        // 这里需要实现ServerAdapter接口
        // 暂时返回null，后续需要实现具体的服务器适配器
        return new A2AServer(agentCard, null);
    }
}
