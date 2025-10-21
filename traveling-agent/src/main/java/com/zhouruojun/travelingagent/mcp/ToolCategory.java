package com.zhouruojun.travelingagent.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具分类注解
 * 用于标记工具属于哪些智能体类别
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolCategory {
    
    /**
     * 支持的智能体列表
     */
    String[] agents() default {};
    
    /**
     * 工具分类名称
     */
    String category() default "";
    
    /**
     * 优先级，数字越大优先级越高
     */
    int priority() default 0;
    
    /**
     * 工具描述
     */
    String description() default "";
}


