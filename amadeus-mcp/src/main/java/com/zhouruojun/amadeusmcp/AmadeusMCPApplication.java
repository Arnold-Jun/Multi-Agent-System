package com.zhouruojun.amadeusmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import lombok.extern.slf4j.Slf4j;

/**
 * Amadeus MCP服务器主应用类
 * 提供航班、酒店和目的地体验的MCP工具服务
 */
@SpringBootApplication
@Slf4j
public class AmadeusMCPApplication {
    
    public static void main(String[] args) {
        log.info("启动Amadeus MCP服务器...");
        SpringApplication.run(AmadeusMCPApplication.class, args);
        log.info("Amadeus MCP服务器启动完成 - http://localhost:18090");
    }
}