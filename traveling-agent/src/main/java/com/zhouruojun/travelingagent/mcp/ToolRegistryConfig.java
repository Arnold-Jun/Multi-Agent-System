package com.zhouruojun.travelingagent.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 工具注册表配置
 * 在应用启动时初始化工具与智能体的映射关系
 */
@Slf4j
@Component
public class ToolRegistryConfig implements CommandLineRunner {
    
    @Autowired
    private ToolRegistry toolRegistry;
    
    @Override
    public void run(String... args) throws Exception {
        log.info("开始初始化工具注册表...");
        
        // 注意：本地工具现在通过@ToolCategory注解自动注册，不需要手动注册
        // registerLocalTools(); // 已移除
        
        // 注册MCP工具
        registerMCPTools();
        
        log.info("工具注册表初始化完成: {}", toolRegistry.getStatistics());
    }

    
    /**
     * 注册MCP工具
     */
    private void registerMCPTools() {
        // MetaSearchAgent - 元搜索智能体工具
        toolRegistry.registerTool("maps_around_search", "metaSearchAgent");
        toolRegistry.registerTool("maps_search_detail", "metaSearchAgent");
        toolRegistry.registerTool("maps_text_search", "metaSearchAgent");
        toolRegistry.registerTool("search_feeds", "metaSearchAgent");
        toolRegistry.registerTool("get_feed_detail", "metaSearchAgent");

        // ItineraryPlannerAgent - 行程规划智能体工具
        toolRegistry.registerTool("maps_direction_driving", "itineraryPlannerAgent");
        toolRegistry.registerTool("maps_direction_bicycling", "itineraryPlannerAgent");
        toolRegistry.registerTool("maps_direction_transit_integrated", "itineraryPlannerAgent");
        toolRegistry.registerTool("maps_direction_walking", "itineraryPlannerAgent");

        // BookingAgent - 预订智能体工具
        //toolRegistry.registerTool("bookFlight", "bookingAgent");


        // OnTripAgent - 出行智能体工具
        toolRegistry.registerTool("maps_search", "onTripAgent");
        
        log.info("MCP工具注册完成");
    }
}
