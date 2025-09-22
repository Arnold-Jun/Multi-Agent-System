package com.zhouruojun.agentcore;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <p>
 * 智能体核心应用程序
 * </p>
 *
 */
@SpringBootApplication(scanBasePackages = { 
    "com.zhouruojun.agentcore",         // 扫描本项目包
    "com.zhouruojun.a2acore"            // A2A核心通信包
})
@Slf4j
public class Application {
    public static void main(String[] args) {
        // 启动独立的主智能体
        log.info("🚀 启动主智能体 (Agent-Core)...");
        System.setProperty("server.port", "8081");
        System.setProperty("spring.application.name", "agent-core");
        
        log.info("✅ 主智能体启动完成！");
        log.info("🌐 服务地址: http://localhost:8081");
        log.info("🔧 健康检查: http://localhost:8081/actuator/health");
        log.info("💬 主智能体聊天: http://localhost:8081/api/agent/chat");
        log.info("🔍 Ollama状态检查: http://localhost:8081/api/agent/ollama/status");
        log.info("🔍 Ollama诊断: http://localhost:8081/api/agent/ollama/diagnose");
        log.info("🔗 A2A通信端点: http://localhost:8081/a2a/");
        
        SpringApplication.run(Application.class, args);
    }
}
