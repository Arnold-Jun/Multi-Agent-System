package com.zhouruojun.jobsearchagent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 求职智能体应用程序
 * Job Search Agent Application
 */
@SpringBootApplication(scanBasePackages = {"com.zhouruojun.jobsearchagent"})
@Slf4j
public class JobSearchApplication {

    public static void main(String[] args) {
        // 启动求职智能体
        log.info("🚀 启动求职智能体 (Job-Search-Agent)...");
        System.setProperty("server.port", "8083");
        System.setProperty("spring.application.name", "job-search-agent");
        
        log.info("✅ 求职智能体启动完成！");
        log.info("🌐 服务地址: http://localhost:8083");
        log.info("🔧 健康检查: http://localhost:8083/actuator/health");
        log.info("💼 求职聊天: http://localhost:8083/api/job-search/chat");
        log.info("🛠️ 求职能力: http://localhost:8083/api/job-search/capabilities");
        log.info("📄 简历上传: http://localhost:8083/api/job-search/upload-resume");
        log.info("🔗 A2A通信端点: http://localhost:8083/a2a/");
        
        SpringApplication.run(JobSearchApplication.class, args);
    }
}
