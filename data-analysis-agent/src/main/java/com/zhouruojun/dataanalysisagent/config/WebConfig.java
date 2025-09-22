package com.zhouruojun.dataanalysisagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 * 配置静态资源和路由
 * 与agent-core保持完全一致
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态资源配置
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600); // 缓存1小时
        
        // 图表文件资源映射
        registry.addResourceHandler("/charts/**")
                .addResourceLocations("file:./data/charts/")
                .setCachePeriod(1800); // 图表缓存30分钟
        
        // 数据文件资源映射        
        registry.addResourceHandler("/data/**")
                .addResourceLocations("file:./data/files/")
                .setCachePeriod(1800);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 根路径重定向到数据分析界面
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/analysis").setViewName("forward:/index.html");
        registry.addViewController("/charts").setViewName("forward:/charts.html");
    }
}

