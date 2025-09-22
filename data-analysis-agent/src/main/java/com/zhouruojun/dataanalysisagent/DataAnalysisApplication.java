package com.zhouruojun.dataanalysisagent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据分析智能体应用程序
 * Data Analysis Agent Application
 */
@SpringBootApplication(scanBasePackages = {"com.zhouruojun.dataanalysisagent"})
@Slf4j
public class DataAnalysisApplication {

    public static void main(String[] args) {
        // 启动数据分析智能体
        log.info("🚀 启动数据分析智能体 (Data-Analysis-Agent)...");
        System.setProperty("server.port", "8082");
        System.setProperty("spring.application.name", "data-analysis-agent");
        
        log.info("✅ 数据分析智能体启动完成！");
        log.info("🌐 服务地址: http://localhost:8082");
        log.info("🔧 健康检查: http://localhost:8082/actuator/health");
        log.info("💬 数据分析聊天: http://localhost:8082/api/data-analysis/chat");
        log.info("🛠️ 数据分析能力: http://localhost:8082/api/data-analysis/capabilities");
        log.info("📊 数据上传: http://localhost:8082/api/data-analysis/upload");
        log.info("🔗 A2A通信端点: http://localhost:8082/a2a/");
        
        SpringApplication.run(DataAnalysisApplication.class, args);
    }
}
