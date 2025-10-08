package com.zhouruojun.bossmock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Boss直聘Mock API应用启动类
 * 
 * @author zhouruojun
 * @version 1.0.0
 */
@Slf4j
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class BossMockApplication {

    public static void main(String[] args) {
        log.info("🚀 启动Boss直聘Mock API服务...");
        System.setProperty("server.port", "8084");
        System.setProperty("spring.application.name", "boss-mock-api");
        
        log.info("✅ Boss直聘Mock API启动完成！");
        log.info("🌐 服务地址: http://localhost:8084/api");
        log.info("📚 API文档: http://localhost:8084/api/swagger-ui.html");
        log.info("🗄️ 数据库控制台: http://localhost:8084/api/h2-console");
        log.info("📊 健康检查: http://localhost:8084/api/actuator/health");
        
        SpringApplication.run(BossMockApplication.class, args);
    }
}

