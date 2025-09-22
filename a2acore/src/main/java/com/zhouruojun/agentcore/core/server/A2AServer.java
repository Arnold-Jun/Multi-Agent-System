package com.zhouruojun.agentcore.core.server;

import com.zhouruojun.agentcore.core.ServerAdapter;
import com.zhouruojun.agentcore.spec.AgentCard;
import reactor.core.publisher.Mono;

/**
 * <p>
 * A2A服务
 * </p>
 *
 */
public class A2AServer {
    private AgentCard agentCard;
    private ServerAdapter serverAdapter;

    public A2AServer(AgentCard agentCard, ServerAdapter serverAdapter) {
        this.agentCard = agentCard;
        this.serverAdapter = serverAdapter;
    }

    public void close() {
        this.serverAdapter.close();
    }

    public Mono<Void> closeGracefully() {
        return this.serverAdapter.closeGracefully();
    }

}
