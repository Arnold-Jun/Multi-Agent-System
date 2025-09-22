package com.zhouruojun.agentcore.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 * 配置静态资源和路由
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态资源配置
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600); // 缓存1小时
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 根路径重定向到聊天界面
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/chat").setViewName("forward:/index.html");
    }
}

