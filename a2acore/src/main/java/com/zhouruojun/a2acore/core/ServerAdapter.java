package com.zhouruojun.a2acore.core;

import reactor.core.publisher.Mono;

/**
 * <p>
 *
 * </p>
 *
 */
public interface ServerAdapter {
    default void close() {
        this.closeGracefully().subscribe();
    }

    Mono<Void> closeGracefully();

    String EVENT_MESSAGE = "message";
}


