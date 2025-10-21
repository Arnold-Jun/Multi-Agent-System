package com.zhouruojun.travelingagent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 旅游智能体应用程序
 * Traveling Agent Application
 */
@SpringBootApplication(scanBasePackages = {"com.zhouruojun.travelingagent"})
@Slf4j
public class TravelingApplication {

    public static void main(String[] args) {
        // 启动旅游智能体
        log.info("🚀 启动旅游智能体 (Traveling-Agent)...");
        System.setProperty("server.port", "8085");
        System.setProperty("spring.application.name", "traveling-agent");
        
        log.info("✅ 旅游智能体启动完成！");
        log.info("🌐 服务地址: http://localhost:8085");
        log.info("🔧 健康检查: http://localhost:8085/actuator/health");
        log.info("✈️ 旅游聊天: http://localhost:8085/api/traveling/chat");
        log.info("🛠️ 旅游能力: http://localhost:8085/api/traveling/capabilities");
        log.info("📄 旅游计划上传: http://localhost:8085/api/traveling/upload-travel-plan");
        log.info("🔗 A2A通信端点: http://localhost:8085/a2a/");
        
        SpringApplication.run(TravelingApplication.class, args);
    }
}

