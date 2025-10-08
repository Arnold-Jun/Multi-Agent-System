package com.xiaohongshu.codewiz.codewizagent.agentcore.session.json;

import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.controller.AgentWsService;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.json.JsonRpcMethodRegistry;
import javax.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/21 21:09
 */
@Configuration
public class JsonRpcConfig {

    @Resource
    private AgentWsService agentWsService;

    @Bean
    public JsonRpcMethodRegistry jsonRpcMethod() {
        JsonRpcMethodRegistry jsonRpcMethodRegistry = new JsonRpcMethodRegistry();
        jsonRpcMethodRegistry.registerService(agentWsService);
        return jsonRpcMethodRegistry;
    }
}
