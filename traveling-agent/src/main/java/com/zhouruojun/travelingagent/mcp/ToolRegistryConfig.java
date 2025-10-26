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
        toolRegistry.registerTool("maps_weather", "metaSearchAgent");
        toolRegistry.registerTool("search_feeds", "metaSearchAgent");
        toolRegistry.registerTool("get_feed_detail", "metaSearchAgent");
        toolRegistry.registerTool("search_activities", "metaSearchAgent");
        toolRegistry.registerTool("get_activity_details", "metaSearchAgent");
        toolRegistry.registerTool("search_locations ", "metaSearchAgent");
        toolRegistry.registerTool("search_flights", "metaSearchAgent");
        toolRegistry.registerTool("search_hotels", "metaSearchAgent");
//        toolRegistry.registerTool("airbnb_search", "metaSearchAgent");
//        toolRegistry.registerTool("airbnb_listing_details", "metaSearchAgent");


        // ItineraryPlannerAgent - 行程规划智能体工具
        toolRegistry.registerTool("maps_direction_driving", "itineraryPlannerAgent");
        toolRegistry.registerTool("maps_direction_bicycling", "itineraryPlannerAgent");
        toolRegistry.registerTool("maps_direction_transit_integrated", "itineraryPlannerAgent");
        toolRegistry.registerTool("maps_direction_walking", "itineraryPlannerAgent");

        // BookingAgent - 预订智能体工具
        toolRegistry.registerTool("get-current-date", "bookingAgent");
        toolRegistry.registerTool("get-stations-code-in-city", "bookingAgent");
        toolRegistry.registerTool("get-station-code-of-citys", "bookingAgent");
        toolRegistry.registerTool("get-station-code-by-names", "bookingAgent");
        toolRegistry.registerTool("get-station-by-telecode", "bookingAgent");
        toolRegistry.registerTool("get-tickets", "bookingAgent");
        toolRegistry.registerTool("get-interline-tickets", "bookingAgent");
        toolRegistry.registerTool("get-train-route-stations", "bookingAgent");

        toolRegistry.registerTool("search_locations ", "bookingAgent");
        toolRegistry.registerTool("search_flights", "bookingAgent");
        toolRegistry.registerTool("search_flight_dates", "bookingAgent");
        toolRegistry.registerTool("search_flight_destinations", "bookingAgent");
        toolRegistry.registerTool("get_seat_map", "bookingAgent");
        toolRegistry.registerTool("create_flight_order", "bookingAgent");
        toolRegistry.registerTool("cancel_flight_order", "bookingAgent");
        toolRegistry.registerTool("search_hotels", "bookingAgent");
        toolRegistry.registerTool("get_hotel_sentiments", "bookingAgent");
        toolRegistry.registerTool("create_hotel_order", "bookingAgent");
        toolRegistry.registerTool("create_transfer_order", "bookingAgent");
        toolRegistry.registerTool("get_transfer_order", "bookingAgent");
        toolRegistry.registerTool("searchFlightsByDepArr", "bookingAgent");
        toolRegistry.registerTool("searchFlightsByDepArr", "bookingAgent");
//        variflight工具
//        toolRegistry.registerTool("searchFlightsByDepArr", "bookingAgent");
//        toolRegistry.registerTool("searchFlightsByNumber", "bookingAgent");
//        toolRegistry.registerTool("getFlightTransferInfo", "bookingAgent");
//        toolRegistry.registerTool("flightHappinessIndex", "bookingAgent");
//        toolRegistry.registerTool("getRealtimeLocationByAnum", "bookingAgent");
//        toolRegistry.registerTool("getFutureWeatherByAirport", "bookingAgent");
//        toolRegistry.registerTool("searchFlightItineraries", "bookingAgent");

        // OnTripAgent - 出行智能体工具
        toolRegistry.registerTool("predict_airport_ontime ", "onTripAgent");
        toolRegistry.registerTool("predict_flight_delay", "bookingAgent");
        toolRegistry.registerTool("get_flight_status", "bookingAgent");
        toolRegistry.registerTool("get_flight_order", "bookingAgent");
        toolRegistry.registerTool("searchFlightsByDepArr", "bookingAgent");

        log.info("MCP工具注册完成");
    }
}
