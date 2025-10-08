package com.zhouruojun.a2acore.core.server;

import com.zhouruojun.a2acore.core.ServerAdapter;
import com.zhouruojun.a2acore.spec.AgentCard;
import reactor.core.publisher.Mono;

/**
 * <p>
 * </p>
 *
 */
public class A2AServer {
    private final AgentCard agentCard;
    private final ServerAdapter serverAdapter;

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


