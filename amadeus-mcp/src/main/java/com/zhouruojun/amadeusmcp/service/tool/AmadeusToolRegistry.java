package com.zhouruojun.amadeusmcp.service.tool;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Amadeus工具注册表
 * 负责管理所有可用的Amadeus工具定义
 */
@Component
public class AmadeusToolRegistry {

    /**
     * 创建工具定义
     */
    private Map<String, Object> createTool(String name, String displayName, String description, Map<String, Object> schema) {
        return Map.of(
            "name", name,
            "displayName", displayName,
            "description", description,
            "inputSchema", schema
        );
    }

    /**
     * 获取所有可用的工具列表
     */
    public List<Map<String, Object>> getAllTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // 添加航班相关工具
        tools.addAll(getFlightTools());
        
        // 添加酒店相关工具
        tools.addAll(getHotelTools());
        
        // 添加活动相关工具
        tools.addAll(getActivityTools());
        
        // 添加位置和参考数据工具
        tools.addAll(getLocationTools());
        
        // 添加预测和分析工具
        tools.addAll(getAnalyticsTools());
        
        // 添加预订工具
        tools.addAll(getBookingTools());
        
        return tools;
    }

    /**
     * 获取航班相关工具
     */
    private List<Map<String, Object>> getFlightTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        tools.add(createTool(
            "search_flights",
            "搜索航班",
            "搜索航班报价信息。注意：departureDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originLocationCode", Map.of("type", "string", "description", "出发地机场代码 (3位英文大写字母，如NYC, LON)"),
                    "destinationLocationCode", Map.of("type", "string", "description", "目的地机场代码 (3位英文大写字母，如NYC, LON)"),
                    "departureDate", Map.of("type", "string", "description", "出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后"),
                    "adults", Map.of("type", "integer", "description", "成人数量，默认1")
                ),
                "required", Arrays.asList("originLocationCode", "destinationLocationCode", "departureDate")
            )
        ));

        tools.add(createTool(
            "search_flight_dates",
            "搜索航班日期",
            "搜索特定航线的可用航班日期和价格信息。注意：departureDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originLocationCode", Map.of("type", "string", "description", "出发地机场代码 (3位英文大写字母，如NYC, LON)"),
                    "destinationLocationCode", Map.of("type", "string", "description", "目的地机场代码 (3位英文大写字母，如NYC, LON)"),
                    "departureDate", Map.of("type", "string", "description", "出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后"),
                    "viewBy", Map.of("type", "string", "description", "视图方式：DURATION, DATE, WEEK")
                ),
                "required", Arrays.asList("originLocationCode", "destinationLocationCode")
            )
        ));

        tools.add(createTool(
            "search_flight_destinations",
            "搜索航班目的地",
            "搜索从特定出发地可到达的目的地及其航班信息。注意：departureDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originLocationCode", Map.of("type", "string", "description", "出发地机场代码 (3位英文大写字母，如NYC, LON)"),
                    "departureDate", Map.of("type", "string", "description", "出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后"),
                    "oneWay", Map.of("type", "boolean", "description", "是否单程，默认false")
                ),
                "required", Arrays.asList("originLocationCode")
            )
        ));

        tools.add(createTool(
            "get_seat_map_by_order",
            "通过订单ID获取座位图",
            "根据航班订单ID获取座位图信息",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "flightOrderId", Map.of("type", "string", "description", "航班订单ID (英文ID)")
                ),
                "required", Arrays.asList("flightOrderId")
            )
        ));

        tools.add(createTool(
            "get_seat_map_by_offer",
            "通过报价数据获取座位图",
            "根据航班报价数据获取座位图信息",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "flightOfferData", Map.of("type", "string", "description", "航班报价数据(JSON字符串)")
                ),
                "required", Arrays.asList("flightOfferData")
            )
        ));

        tools.add(createTool(
            "predict_flight_delay",
            "预测航班延误",
            "预测航班延误概率。注意：departureDate和arrivalDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originLocationCode", Map.of("type", "string", "description", "出发地机场代码 (3位英文大写字母，如NYC, LON)"),
                    "destinationLocationCode", Map.of("type", "string", "description", "目的地机场代码 (3位英文大写字母，如NYC, LON)"),
                    "departureDate", Map.of("type", "string", "description", "出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后"),
                    "departureTime", Map.of("type", "string", "description", "出发时间 (HH:mm:ss)"),
                    "arrivalDate", Map.of("type", "string", "description", "到达日期 (YYYY-MM-DD)，必须是未来日期"),
                    "arrivalTime", Map.of("type", "string", "description", "到达时间 (HH:mm:ss)"),
                    "aircraftCode", Map.of("type", "string", "description", "飞机代码 (英文代码，如320, 737)"),
                    "carrierCode", Map.of("type", "string", "description", "航空公司代码 (2位英文大写字母，如AA, BA)"),
                    "flightNumber", Map.of("type", "string", "description", "航班号 (数字，如123, 456)"),
                    "duration", Map.of("type", "string", "description", "飞行时长 (PT格式)")
                ),
                "required", Arrays.asList("originLocationCode", "destinationLocationCode", "departureDate", "departureTime", "arrivalDate", "arrivalTime", "aircraftCode", "carrierCode", "flightNumber", "duration")
            )
        ));

        tools.add(createTool(
            "get_flight_status",
            "查询航班状态",
            "查询实时航班状态。注意：scheduledDepartureDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "carrierCode", Map.of("type", "string", "description", "航空公司代码 (2位英文大写字母，如AA, BA)"),
                    "flightNumber", Map.of("type", "string", "description", "航班号 (数字，如123, 456)"),
                    "scheduledDepartureDate", Map.of("type", "string", "description", "计划出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后")
                ),
                "required", Arrays.asList("carrierCode", "flightNumber", "scheduledDepartureDate")
            )
        ));

        tools.add(createTool(
            "predict_airport_ontime",
            "预测机场准点率",
            "预测机场航班准点率。注意：date必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "airportCode", Map.of("type", "string", "description", "机场代码 (3位英文大写字母，如NYC, LON)"),
                    "date", Map.of("type", "string", "description", "日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后")
                ),
                "required", Arrays.asList("airportCode", "date")
            )
        ));

        return tools;
    }

    /**
     * 获取酒店相关工具
     */
    private List<Map<String, Object>> getHotelTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        tools.add(createTool(
            "search_hotels",
            "搜索酒店",
            "根据城市代码搜索酒店基本信息列表，返回酒店的基本信息如名称、地址、位置等。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "cityCode", Map.of("type", "string", "description", "城市代码 (英文代码，如NYC, LON)")
                ),
                "required", Arrays.asList("cityCode")
            )
        ));

        tools.add(createTool(
            "get_hotel_offers",
            "获取酒店报价",
            "获取特定酒店的详细报价信息。注意：checkInDate和checkOutDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "hotelIds", Map.of("type", "string", "description", "酒店ID (英文ID，多个用逗号分隔)"),
                    "checkInDate", Map.of("type", "string", "description", "入住日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后"),
                    "checkOutDate", Map.of("type", "string", "description", "退房日期 (YYYY-MM-DD)，必须是未来日期"),
                    "adults", Map.of("type", "integer", "description", "成人数量，默认1")
                ),
                "required", Arrays.asList("hotelIds", "checkInDate", "checkOutDate")
            )
        ));

        tools.add(createTool(
            "get_hotel_sentiments",
            "获取酒店声誉分析",
            "获取基于情感分析的酒店评分",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "hotelIds", Map.of("type", "string", "description", "酒店ID，多个用逗号分隔")
                ),
                "required", Arrays.asList("hotelIds")
            )
        ));

        return tools;
    }

    /**
     * 获取活动相关工具
     */
    private List<Map<String, Object>> getActivityTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        tools.add(createTool(
            "search_activities",
            "搜索目的地活动",
            "搜索特定目的地的活动和体验",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "latitude", Map.of("type", "number", "description", "纬度"),
                    "longitude", Map.of("type", "number", "description", "经度"),
                    "radius", Map.of("type", "integer", "description", "搜索半径（公里），默认50")
                ),
                "required", Arrays.asList("latitude", "longitude")
            )
        ));

        tools.add(createTool(
            "get_activity_details",
            "获取活动详情",
            "获取特定活动的详细信息",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "activityId", Map.of("type", "string", "description", "活动ID (英文ID)")
                ),
                "required", Arrays.asList("activityId")
            )
        ));

        return tools;
    }

    /**
     * 获取位置和参考数据工具
     */
    private List<Map<String, Object>> getLocationTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        tools.add(createTool(
            "search_locations",
            "搜索位置",
            "搜索机场、城市等位置信息",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "keyword", Map.of("type", "string", "description", "搜索关键词 (请使用英文英文，如New York, London)"),
                    "subType", Map.of("type", "string", "description", "子类型：AIRPORT, CITY, ANY")
                ),
                "required", Arrays.asList("keyword")
            )
        ));

        tools.add(createTool(
            "get_airport_destinations",
            "获取机场直达目的地",
            "获取机场直达目的地列表",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "departureAirportCode", Map.of("type", "string", "description", "出发机场代码 (3位英文大写字母，如NYC, LON)"),
                    "max", Map.of("type", "integer", "description", "最大返回数量，默认10")
                ),
                "required", Arrays.asList("departureAirportCode")
            )
        ));

        tools.add(createTool(
            "get_airline_destinations",
            "获取航空公司目的地",
            "获取航空公司服务的目的地",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "airlineCode", Map.of("type", "string", "description", "航空公司代码 (2位英文大写字母，如AA, BA)"),
                    "max", Map.of("type", "integer", "description", "最大返回数量，默认10")
                ),
                "required", Arrays.asList("airlineCode")
            )
        ));

        return tools;
    }

    /**
     * 获取预测和分析工具
     */
    private List<Map<String, Object>> getAnalyticsTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        tools.add(createTool(
            "predict_trip_purpose",
            "预测旅行目的",
            "预测旅行目的。注意：departureDate和returnDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originLocationCode", Map.of("type", "string", "description", "出发地机场代码 (3位英文大写字母，如NYC, LON)"),
                    "destinationLocationCode", Map.of("type", "string", "description", "目的地机场代码 (3位英文大写字母，如NYC, LON)"),
                    "departureDate", Map.of("type", "string", "description", "出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后"),
                    "returnDate", Map.of("type", "string", "description", "返程日期 (YYYY-MM-DD)，必须是未来日期")
                ),
                "required", Arrays.asList("originLocationCode", "destinationLocationCode", "departureDate", "returnDate")
            )
        ));

        tools.add(createTool(
            "get_booked_air_traffic",
            "获取预订航空交通分析",
            "获取预订航空交通分析数据",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originCityCode", Map.of("type", "string", "description", "出发城市代码 (英文代码，如NYC, LON)"),
                    "period", Map.of("type", "string", "description", "时间段 (YYYY-MM)")
                ),
                "required", Arrays.asList("originCityCode", "period")
            )
        ));

        tools.add(createTool(
            "get_traveled_air_traffic",
            "获取旅行航空交通分析",
            "获取旅行航空交通分析数据",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originCityCode", Map.of("type", "string", "description", "出发城市代码 (英文代码，如NYC, LON)"),
                    "period", Map.of("type", "string", "description", "时间段 (YYYY-MM)")
                ),
                "required", Arrays.asList("originCityCode", "period")
            )
        ));

        tools.add(createTool(
            "get_busiest_air_traffic",
            "获取最繁忙航空交通分析",
            "获取最繁忙航空交通分析数据",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originCityCode", Map.of("type", "string", "description", "出发城市代码 (英文代码，如NYC, LON)"),
                    "period", Map.of("type", "string", "description", "时间段 (YYYY-MM)"),
                    "direction", Map.of("type", "string", "description", "方向: ARRIVING, DEPARTING")
                ),
                "required", Arrays.asList("originCityCode", "period", "direction")
            )
        ));

        tools.add(createTool(
            "get_price_analytics",
            "获取价格分析",
            "获取行程价格指标分析。注意：departureDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originIataCode", Map.of("type", "string", "description", "出发地IATA代码 (3位英文大写字母，如NYC, LON)"),
                    "destinationIataCode", Map.of("type", "string", "description", "目的地IATA代码 (3位英文大写字母，如NYC, LON)"),
                    "departureDate", Map.of("type", "string", "description", "出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后")
                ),
                "required", Arrays.asList("originIataCode", "destinationIataCode", "departureDate")
            )
        ));

        return tools;
    }

    /**
     * 获取预订工具
     */
    private List<Map<String, Object>> getBookingTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        tools.add(createTool(
            "create_flight_order",
            "创建航班订单",
            "创建航班预订订单，需要提供航班报价和旅客信息",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "flightOfferData", Map.of("type", "string", "description", "航班报价数据(JSON字符串)"),
                    "travelers", Map.of("type", "array", "description", "旅客信息数组，每个旅客包含姓名、出生日期、联系方式等"),
                    "contacts", Map.of("type", "array", "description", "联系人信息数组，可选")
                ),
                "required", Arrays.asList("flightOfferData", "travelers")
            )
        ));

        tools.add(createTool(
            "get_flight_order",
            "查询航班订单",
            "根据订单ID查询航班订单详情",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "flightOrderId", Map.of("type", "string", "description", "航班订单ID (英文ID)")
                ),
                "required", Arrays.asList("flightOrderId")
            )
        ));

        tools.add(createTool(
            "cancel_flight_order",
            "取消航班订单",
            "根据订单ID取消航班订单",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "flightOrderId", Map.of("type", "string", "description", "航班订单ID (英文ID)")
                ),
                "required", Arrays.asList("flightOrderId")
            )
        ));

        tools.add(createTool(
            "create_hotel_order",
            "创建酒店订单",
            "创建酒店预订订单，需要提供酒店报价和旅客信息",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "hotelOfferData", Map.of("type", "string", "description", "酒店报价数据(JSON字符串)"),
                    "guests", Map.of("type", "array", "description", "客人信息数组"),
                    "payments", Map.of("type", "array", "description", "支付信息数组"),
                    "remarks", Map.of("type", "object", "description", "备注信息，可选")
                ),
                "required", Arrays.asList("hotelOfferData", "guests", "payments")
            )
        ));

        tools.add(createTool(
            "create_transfer_order",
            "创建接送服务订单",
            "创建机场接送服务预订订单",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "transferOfferData", Map.of("type", "string", "description", "接送服务报价数据(JSON字符串)"),
                    "passengers", Map.of("type", "array", "description", "乘客信息数组"),
                    "contacts", Map.of("type", "array", "description", "联系人信息数组")
                ),
                "required", Arrays.asList("transferOfferData", "passengers", "contacts")
            )
        ));

        tools.add(createTool(
            "get_transfer_order",
            "查询接送服务订单",
            "根据订单ID查询接送服务订单详情",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "transferOrderId", Map.of("type", "string", "description", "接送服务订单ID (英文ID)")
                ),
                "required", Arrays.asList("transferOrderId")
            )
        ));

        return tools;
    }
}
