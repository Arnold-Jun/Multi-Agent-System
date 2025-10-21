package com.zhouruojun.mcpserver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP服务器主应用类
 * 提供MCP协议代理服务，支持通过mcp.json动态配置MCP服务器
 */
@SpringBootApplication
@Slf4j
public class MCPServerApplication {

    public static void main(String[] args) {
        log.info("🚀 启动MCP代理服务器...");
        SpringApplication.run(MCPServerApplication.class, args);
        log.info("✅ MCP服务器启动完成！");
        log.info("🌐 服务地址: http://localhost:18083");
        log.info("📝 配置文件: src/main/resources/mcp.json");
        log.info("📡 可用端点：");
        log.info("  🔧 健康检查:     GET  /mcp/health");
        log.info("  📊 服务器信息:   GET  /mcp/info");
        log.info("  🔌 MCP协议端点:  POST /mcp/jsonrpc");
        log.info("  📋 可用服务器:   GET  /mcp/servers");
        log.info("  📈 会话统计:     GET  /mcp/sessions");
        log.info("  🔄 重新加载配置: POST /mcp/reload");
    }
}
