package com.xiaohongshu.codewiz.codewizagent.agentcore.session.json;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法为 JSON-RPC 可调用的方法
 */
@Retention(RetentionPolicy.RUNTIME) // 运行时可见
@Target(ElementType.METHOD)         // 只能修饰方法
public @interface JsonRpcMethod {
    /**
     * JSON-RPC 方法名（如 "agent/chat"）
     */
    String value();
}