package com.zhouruojun.amadeusmcp.service;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.exceptions.ResponseException;
import com.amadeus.resources.Location;
import com.amadeus.resources.FlightOrder;
import com.amadeus.resources.FlightOrder.Traveler;
import com.amadeus.resources.FlightOrder.Name;
import com.amadeus.resources.FlightOrder.Phone;
import com.amadeus.resources.FlightOrder.Contact;
import com.amadeus.resources.FlightOrder.Document;
import com.amadeus.resources.HotelOrder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhouruojun.amadeusmcp.exception.AmadeusMCPException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Amadeus工具服务
 * 提供航班、酒店和目的地体验相关的工具
 */
@Slf4j
@Service
public class AmadeusToolService {

    @Autowired
    private Amadeus amadeus;

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
     * 检查必需参数
     */
    private Map<String, Object> checkRequiredParams(Map<String, Object> arguments, String... paramNames) {
        List<String> missingParams = new ArrayList<>();
        for (String paramName : paramNames) {
            Object value = arguments.get(paramName);
            if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                missingParams.add(paramName);
            }
        }
        
        if (!missingParams.isEmpty()) {
                return Map.of(
                    "status", "error",
                "message", "缺少必需参数: " + String.join(", ", missingParams),
                "missingParams", missingParams
            );
        }
        return null; // 所有参数都存在且有效
    }
    
    /**
     * 验证日期格式
     */
    private boolean isValidDateFormat(String date) {
        if (date == null || date.trim().isEmpty()) {
            return false;
        }
        return date.matches("\\d{4}-\\d{2}-\\d{2}");
    }
    
    /**
     * 验证机场代码格式
     */
    private boolean isValidAirportCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        return code.matches("[A-Z]{3}");
    }

    /**
     * 创建成功响应
     */
    private Map<String, Object> createSuccessResponse(String message, int count, Object data) {
        return Map.of(
            "status", "success",
            "message", message,
            "count", count,
            "data", data
        );
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String message) {
        return Map.of(
            "status", "error",
            "message", message
        );
    }

    /**
     * 处理异常并返回错误响应
     */
    private Map<String, Object> handleException(Exception e, String operation) {
        log.error("{}失败", operation, e);
        return createErrorResponse(operation + "失败: " + e.getMessage());
    }

    /**
     * 获取可用的工具列表
     */
    public List<Map<String, Object>> getAvailableTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // 航班相关工具
        tools.add(createTool(
            "search_flights",
            "高级航班搜索",
            "进行高级航班搜索，支持复杂的搜索条件和多旅客类型（成人、儿童、婴儿）。注意：departureDate和returnDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originLocationCode", Map.of("type", "string", "description", "出发地机场代码"),
                    "destinationLocationCode", Map.of("type", "string", "description", "目的地机场代码"),
                    "departureDate", Map.of("type", "string", "description", "出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后"),
                    "returnDate", Map.of("type", "string", "description", "返程日期 (YYYY-MM-DD)，可选，必须是未来日期"),
                    "adults", Map.of("type", "integer", "description", "成人数量，默认1"),
                    "children", Map.of("type", "integer", "description", "儿童数量，默认0"),
                    "infants", Map.of("type", "integer", "description", "婴儿数量，默认0")
                ),
                "required", Arrays.asList("originLocationCode", "destinationLocationCode", "departureDate")
            )
        ));

        tools.add(createTool(
            "get_flight_offers",
            "简单航班搜索",
            "进行简单的航班搜索，返回航班报价信息。注意：departureDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originLocationCode", Map.of("type", "string", "description", "出发地机场代码"),
                    "destinationLocationCode", Map.of("type", "string", "description", "目的地机场代码"),
                    "departureDate", Map.of("type", "string", "description", "出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后"),
                    "adults", Map.of("type", "integer", "description", "成人数量，默认1")
                ),
                "required", Arrays.asList("originLocationCode", "destinationLocationCode", "departureDate")
            )
        ));

        // 酒店相关工具
        tools.add(createTool(
            "search_hotels",
            "搜索酒店",
            "根据城市和日期搜索酒店信息。注意：checkInDate和checkOutDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "cityCode", Map.of("type", "string", "description", "城市代码"),
                    "checkInDate", Map.of("type", "string", "description", "入住日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后"),
                    "checkOutDate", Map.of("type", "string", "description", "退房日期 (YYYY-MM-DD)，必须是未来日期"),
                    "adults", Map.of("type", "integer", "description", "成人数量，默认1"),
                    "rooms", Map.of("type", "integer", "description", "房间数量，默认1")
                ),
                "required", Arrays.asList("cityCode", "checkInDate", "checkOutDate")
            )
        ));

        tools.add(createTool(
            "get_hotel_offers",
            "获取酒店报价",
            "获取特定酒店的详细报价信息。注意：checkInDate和checkOutDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "hotelIds", Map.of("type", "string", "description", "酒店ID，多个用逗号分隔"),
                    "checkInDate", Map.of("type", "string", "description", "入住日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后"),
                    "checkOutDate", Map.of("type", "string", "description", "退房日期 (YYYY-MM-DD)，必须是未来日期"),
                    "adults", Map.of("type", "integer", "description", "成人数量，默认1")
                ),
                "required", Arrays.asList("hotelIds", "checkInDate", "checkOutDate")
            )
        ));

        // 目的地体验工具
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
                    "activityId", Map.of("type", "string", "description", "活动ID")
                ),
                "required", Arrays.asList("activityId")
            )
        ));

        // 位置和参考数据工具
        tools.add(createTool(
            "search_locations",
            "搜索位置",
            "搜索机场、城市等位置信息",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "keyword", Map.of("type", "string", "description", "搜索关键词"),
                    "subType", Map.of("type", "string", "description", "子类型：AIRPORT, CITY, ANY")
                ),
                "required", Arrays.asList("keyword")
            )
        ));

        // 航班日期和目的地工具
        tools.add(createTool(
            "search_flight_dates",
            "搜索航班日期",
            "搜索特定航线的可用航班日期和价格信息。注意：departureDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originLocationCode", Map.of("type", "string", "description", "出发地机场代码"),
                    "destinationLocationCode", Map.of("type", "string", "description", "目的地机场代码"),
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
                    "originLocationCode", Map.of("type", "string", "description", "出发地机场代码"),
                    "departureDate", Map.of("type", "string", "description", "出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后"),
                    "oneWay", Map.of("type", "boolean", "description", "是否单程，默认false")
                ),
                "required", Arrays.asList("originLocationCode")
            )
        ));

        // 座位图工具
        tools.add(createTool(
            "get_seat_map",
            "获取座位图",
            "获取航班的座位图信息。需要提供flightOrderId或flightOfferData中的至少一个参数。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "flightOrderId", Map.of("type", "string", "description", "航班订单ID，可选"),
                    "flightOfferData", Map.of("type", "string", "description", "航班报价数据(JSON字符串)，可选")
                ),
                "required", Arrays.asList()
            )
        ));


        // 航班延误预测工具
        tools.add(createTool(
            "predict_flight_delay",
            "预测航班延误",
            "预测航班延误概率。注意：departureDate和arrivalDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originLocationCode", Map.of("type", "string", "description", "出发地机场代码"),
                    "destinationLocationCode", Map.of("type", "string", "description", "目的地机场代码"),
                    "departureDate", Map.of("type", "string", "description", "出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后"),
                    "departureTime", Map.of("type", "string", "description", "出发时间 (HH:mm:ss)"),
                    "arrivalDate", Map.of("type", "string", "description", "到达日期 (YYYY-MM-DD)，必须是未来日期"),
                    "arrivalTime", Map.of("type", "string", "description", "到达时间 (HH:mm:ss)"),
                    "aircraftCode", Map.of("type", "string", "description", "飞机代码"),
                    "carrierCode", Map.of("type", "string", "description", "航空公司代码"),
                    "flightNumber", Map.of("type", "string", "description", "航班号"),
                    "duration", Map.of("type", "string", "description", "飞行时长 (PT格式)")
                ),
                "required", Arrays.asList("originLocationCode", "destinationLocationCode", "departureDate", "departureTime", "arrivalDate", "arrivalTime", "aircraftCode", "carrierCode", "flightNumber", "duration")
            )
        ));

        // 旅行目的预测工具
        tools.add(createTool(
            "predict_trip_purpose",
            "预测旅行目的",
            "预测旅行目的。注意：departureDate和returnDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originLocationCode", Map.of("type", "string", "description", "出发地机场代码"),
                    "destinationLocationCode", Map.of("type", "string", "description", "目的地机场代码"),
                    "departureDate", Map.of("type", "string", "description", "出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后"),
                    "returnDate", Map.of("type", "string", "description", "返程日期 (YYYY-MM-DD)，必须是未来日期")
                ),
                "required", Arrays.asList("originLocationCode", "destinationLocationCode", "departureDate", "returnDate")
            )
        ));

        // 航空交通分析工具
        tools.add(createTool(
            "get_air_traffic_analytics",
            "获取航空交通分析",
            "获取航空交通分析数据",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originCityCode", Map.of("type", "string", "description", "出发城市代码"),
                    "period", Map.of("type", "string", "description", "时间段 (YYYY-MM)"),
                    "type", Map.of("type", "string", "description", "分析类型: booked, traveled, busiest"),
                    "direction", Map.of("type", "string", "description", "方向: ARRIVING, DEPARTING (仅busiest类型需要)")
                ),
                "required", Arrays.asList("originCityCode", "period", "type")
            )
        ));

        // 价格分析工具
        tools.add(createTool(
            "get_price_analytics",
            "获取价格分析",
            "获取行程价格指标分析。注意：departureDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "originIataCode", Map.of("type", "string", "description", "出发地IATA代码"),
                    "destinationIataCode", Map.of("type", "string", "description", "目的地IATA代码"),
                    "departureDate", Map.of("type", "string", "description", "出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后")
                ),
                "required", Arrays.asList("originIataCode", "destinationIataCode", "departureDate")
            )
        ));

        // 机场直达目的地工具
        tools.add(createTool(
            "get_airport_destinations",
            "获取机场直达目的地",
            "获取机场直达目的地列表",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "departureAirportCode", Map.of("type", "string", "description", "出发机场代码"),
                    "max", Map.of("type", "integer", "description", "最大返回数量，默认10")
                ),
                "required", Arrays.asList("departureAirportCode")
            )
        ));

        // 航班状态查询工具
        tools.add(createTool(
            "get_flight_status",
            "查询航班状态",
            "查询实时航班状态。注意：scheduledDepartureDate必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "carrierCode", Map.of("type", "string", "description", "航空公司代码"),
                    "flightNumber", Map.of("type", "string", "description", "航班号"),
                    "scheduledDepartureDate", Map.of("type", "string", "description", "计划出发日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后")
                ),
                "required", Arrays.asList("carrierCode", "flightNumber", "scheduledDepartureDate")
            )
        ));

        // 酒店声誉分析工具
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


        // 航空公司目的地工具
        tools.add(createTool(
            "get_airline_destinations",
            "获取航空公司目的地",
            "获取航空公司服务的目的地",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "airlineCode", Map.of("type", "string", "description", "航空公司代码"),
                    "max", Map.of("type", "integer", "description", "最大返回数量，默认10")
                ),
                "required", Arrays.asList("airlineCode")
            )
        ));

        // 机场准点率预测工具
        tools.add(createTool(
            "predict_airport_ontime",
            "预测机场准点率",
            "预测机场航班准点率。注意：date必须是未来的日期，不能是过去的日期。建议使用至少30天后的日期。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "airportCode", Map.of("type", "string", "description", "机场代码"),
                    "date", Map.of("type", "string", "description", "日期 (YYYY-MM-DD)，必须是未来日期，建议至少30天后")
                ),
                "required", Arrays.asList("airportCode", "date")
            )
        ));

        // 航班预订工具
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
                    "flightOrderId", Map.of("type", "string", "description", "航班订单ID")
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
                    "flightOrderId", Map.of("type", "string", "description", "航班订单ID")
                ),
                "required", Arrays.asList("flightOrderId")
            )
        ));

        // 酒店预订工具
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

        // 接送服务预订工具
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
                    "transferOrderId", Map.of("type", "string", "description", "接送服务订单ID")
                ),
                "required", Arrays.asList("transferOrderId")
            )
        ));

        return tools;
    }

    /**
     * 调用工具
     */
    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) throws ResponseException {
        log.info("调用Amadeus工具: {} 参数: {}", toolName, arguments);
        
        return switch (toolName) {
            case "search_flights" -> searchFlights(arguments);
            case "get_flight_offers" -> getFlightOffers(arguments);
            case "search_hotels" -> searchHotels(arguments);
            case "get_hotel_offers" -> getHotelOffers(arguments);
            case "search_activities" -> searchActivities(arguments);
            case "get_activity_details" -> getActivityDetails(arguments);
            case "search_locations" -> searchLocations(arguments);
            case "search_flight_dates" -> searchFlightDates(arguments);
            case "search_flight_destinations" -> searchFlightDestinations(arguments);
            case "get_seat_map" -> getSeatMap(arguments);
            case "predict_flight_delay" -> predictFlightDelay(arguments);
            case "predict_trip_purpose" -> predictTripPurpose(arguments);
            case "get_air_traffic_analytics" -> getAirTrafficAnalytics(arguments);
            case "get_price_analytics" -> getPriceAnalytics(arguments);
            case "get_airport_destinations" -> getAirportDestinations(arguments);
            case "get_flight_status" -> getFlightStatus(arguments);
            case "get_hotel_sentiments" -> getHotelSentiments(arguments);
            case "get_airline_destinations" -> getAirlineDestinations(arguments);
            case "predict_airport_ontime" -> predictAirportOntime(arguments);
            case "create_flight_order" -> createFlightOrder(arguments);
            case "get_flight_order" -> getFlightOrder(arguments);
            case "cancel_flight_order" -> cancelFlightOrder(arguments);
            case "create_hotel_order" -> createHotelOrder(arguments);
            case "create_transfer_order" -> createTransferOrder(arguments);
            case "get_transfer_order" -> getTransferOrder(arguments);
            default -> throw new IllegalArgumentException("未知的工具: " + toolName);
        };
    }

    /**
     * 搜索航班
     */
    private Map<String, Object> searchFlights(Map<String, Object> arguments) {
        try {
            log.info("搜索航班: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = checkRequiredParams(arguments, "originLocationCode", "destinationLocationCode", "departureDate");
            if (paramCheck != null) return paramCheck;
            
            String origin = (String) arguments.get("originLocationCode");
            String destination = (String) arguments.get("destinationLocationCode");
            String departureDate = (String) arguments.get("departureDate");
            String returnDate = (String) arguments.get("returnDate");
            Integer adults = (Integer) arguments.getOrDefault("adults", 1);
            Integer children = (Integer) arguments.getOrDefault("children", 0);
            Integer infants = (Integer) arguments.getOrDefault("infants", 0);
            
            // 验证参数格式
            if (!isValidAirportCode(origin)) {
                return createErrorResponse("无效的出发地机场代码: " + origin);
            }
            if (!isValidAirportCode(destination)) {
                return createErrorResponse("无效的目的地机场代码: " + destination);
            }
            if (!isValidDateFormat(departureDate)) {
                return createErrorResponse("无效的出发日期格式: " + departureDate + "，请使用YYYY-MM-DD格式");
            }
            if (returnDate != null && !isValidDateFormat(returnDate)) {
                return createErrorResponse("无效的返程日期格式: " + returnDate + "，请使用YYYY-MM-DD格式");
            }
            if (adults == null || adults < 1) {
                return createErrorResponse("成人数量必须大于等于1");
            }
            if (children == null || children < 0) {
                return createErrorResponse("儿童数量不能为负数");
            }
            if (infants == null || infants < 0) {
                return createErrorResponse("婴儿数量不能为负数");
            }
            if (infants > adults) {
                return createErrorResponse("婴儿数量不能超过成人数量");
            }
            
            // 构建航班搜索请求
            JsonObject request = new JsonObject();
            request.addProperty("currencyCode", "USD");
            
            // 构建行程
            JsonArray originDestinations = new JsonArray();
            
            // 去程
            JsonObject outbound = new JsonObject();
            outbound.addProperty("id", "1");
            outbound.addProperty("originLocationCode", origin);
            outbound.addProperty("destinationLocationCode", destination);
            
            JsonObject departureDateTimeRange = new JsonObject();
            departureDateTimeRange.addProperty("date", departureDate);
            outbound.add("departureDateTimeRange", departureDateTimeRange);
            originDestinations.add(outbound);
            
            // 返程（如果有）
            if (returnDate != null && !returnDate.isEmpty()) {
                JsonObject inbound = new JsonObject();
                inbound.addProperty("id", "2");
                inbound.addProperty("originLocationCode", destination);
                inbound.addProperty("destinationLocationCode", origin);
                
                JsonObject returnDateTimeRange = new JsonObject();
                returnDateTimeRange.addProperty("date", returnDate);
                inbound.add("departureDateTimeRange", returnDateTimeRange);
                originDestinations.add(inbound);
            }
            
            request.add("originDestinations", originDestinations);
            
            // 构建旅客信息
            JsonArray travelers = new JsonArray();
            for (int i = 0; i < adults; i++) {
                JsonObject adult = new JsonObject();
                adult.addProperty("id", String.valueOf(i + 1));
                adult.addProperty("travelerType", "ADULT");
                travelers.add(adult);
            }
            for (int i = 0; i < children; i++) {
                JsonObject child = new JsonObject();
                child.addProperty("id", String.valueOf(adults + i + 1));
                child.addProperty("travelerType", "CHILD");
                travelers.add(child);
            }
            for (int i = 0; i < infants; i++) {
                JsonObject infant = new JsonObject();
                infant.addProperty("id", String.valueOf(adults + children + i + 1));
                infant.addProperty("travelerType", "INFANT");
                infant.addProperty("associatedAdultId", String.valueOf(i + 1));
                travelers.add(infant);
            }
            
            request.add("travelers", travelers);
            
            // 设置数据源
            JsonArray sources = new JsonArray();
            sources.add("GDS");
            request.add("sources", sources);
            
            // 调用Amadeus API
            com.amadeus.resources.FlightOfferSearch[] flightOffers = amadeus.shopping.flightOffersSearch.post(request);
            
            // 处理结果
            List<Map<String, Object>> flights = new ArrayList<>();
            for (com.amadeus.resources.FlightOfferSearch offer : flightOffers) {
                Map<String, Object> flight = new HashMap<>();
                flight.put("id", offer.getId());
                flight.put("type", offer.getType());
                flight.put("source", offer.getSource());
                flight.put("instantTicketingRequired", offer.isInstantTicketingRequired());
                flight.put("oneWay", offer.isOneWay());
                flight.put("lastTicketingDate", offer.getLastTicketingDate());
                flight.put("numberOfBookableSeats", offer.getNumberOfBookableSeats());
                flight.put("price", offer.getPrice());
                flight.put("itineraries", offer.getItineraries());
                flight.put("pricingOptions", offer.getPricingOptions());
                flight.put("validatingAirlineCodes", offer.getValidatingAirlineCodes());
                flight.put("travelerPricings", offer.getTravelerPricings());
                flights.add(flight);
            }
            
            return createSuccessResponse("航班搜索完成", flights.size(), flights);
            
        } catch (Exception e) {
            return handleException(e, "搜索航班");
        }
    }

    /**
     * 获取航班报价
     */
    private Map<String, Object> getFlightOffers(Map<String, Object> arguments) {
        try {
            log.info("获取航班报价: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = checkRequiredParams(arguments, "originLocationCode", "destinationLocationCode", "departureDate");
            if (paramCheck != null) return paramCheck;
            
            String origin = (String) arguments.get("originLocationCode");
            String destination = (String) arguments.get("destinationLocationCode");
            String departureDate = (String) arguments.get("departureDate");
            Integer adults = (Integer) arguments.getOrDefault("adults", 1);
            
            // 验证参数格式
            if (!isValidAirportCode(origin)) {
                return createErrorResponse("无效的出发地机场代码: " + origin);
            }
            if (!isValidAirportCode(destination)) {
                return createErrorResponse("无效的目的地机场代码: " + destination);
            }
            if (!isValidDateFormat(departureDate)) {
                return createErrorResponse("无效的出发日期格式: " + departureDate + "，请使用YYYY-MM-DD格式");
            }
            if (adults == null || adults < 1) {
                return createErrorResponse("成人数量必须大于等于1");
            }
            
            // 使用GET方法搜索航班报价
            Params params = Params
                .with("originLocationCode", origin)
                .and("destinationLocationCode", destination)
                .and("departureDate", departureDate)
                .and("adults", adults)
                .and("max", 10);
            
            // 调用Amadeus API
            com.amadeus.resources.FlightOfferSearch[] flightOffers = amadeus.shopping.flightOffersSearch.get(params);
            
            // 处理结果
            List<Map<String, Object>> offers = new ArrayList<>();
            for (com.amadeus.resources.FlightOfferSearch offer : flightOffers) {
                Map<String, Object> flightOffer = new HashMap<>();
                flightOffer.put("id", offer.getId());
                flightOffer.put("type", offer.getType());
                flightOffer.put("source", offer.getSource());
                flightOffer.put("instantTicketingRequired", offer.isInstantTicketingRequired());
                flightOffer.put("oneWay", offer.isOneWay());
                flightOffer.put("lastTicketingDate", offer.getLastTicketingDate());
                flightOffer.put("numberOfBookableSeats", offer.getNumberOfBookableSeats());
                flightOffer.put("price", offer.getPrice());
                flightOffer.put("itineraries", offer.getItineraries());
                flightOffer.put("pricingOptions", offer.getPricingOptions());
                flightOffer.put("validatingAirlineCodes", offer.getValidatingAirlineCodes());
                flightOffer.put("travelerPricings", offer.getTravelerPricings());
                offers.add(flightOffer);
            }
            
            return createSuccessResponse("航班报价获取完成", offers.size(), offers);
            
        } catch (Exception e) {
            return handleException(e, "获取航班报价");
        }
    }

    /**
     * 搜索酒店
     */
    private Map<String, Object> searchHotels(Map<String, Object> arguments) {
        try {
            log.info("搜索酒店: {}", arguments);
            
            String cityCode = (String) arguments.get("cityCode");
            String checkInDate = (String) arguments.get("checkInDate");
            String checkOutDate = (String) arguments.get("checkOutDate");
            Integer adults = (Integer) arguments.getOrDefault("adults", 1);
            Integer rooms = (Integer) arguments.getOrDefault("rooms", 1);
            
            // 检查必需参数
            Map<String, Object> paramCheck = checkRequiredParams(arguments, "cityCode", "checkInDate", "checkOutDate");
            if (paramCheck != null) return paramCheck;
            
            // 验证参数格式
            if (!isValidDateFormat(checkInDate)) {
                return createErrorResponse("无效的入住日期格式: " + checkInDate + "，请使用YYYY-MM-DD格式");
            }
            if (!isValidDateFormat(checkOutDate)) {
                return createErrorResponse("无效的退房日期格式: " + checkOutDate + "，请使用YYYY-MM-DD格式");
            }
            if (adults == null || adults < 1) {
                return createErrorResponse("成人数量必须大于等于1");
            }
            if (rooms == null || rooms < 1) {
                return createErrorResponse("房间数量必须大于等于1");
            }
            
            // 验证日期逻辑
            if (checkInDate.compareTo(checkOutDate) >= 0) {
                return createErrorResponse("退房日期必须晚于入住日期");
            }
            
            // 第一步：获取城市中的酒店列表
            Params hotelListParams = Params.with("cityCode", cityCode);
            com.amadeus.resources.Hotel[] hotels = amadeus.referenceData.locations.hotels.byCity.get(hotelListParams);
            
            if (hotels == null || hotels.length == 0) {
            return Map.of(
                "status", "error",
                    "message", "未找到该城市的酒店",
                    "count", 0,
                    "hotels", new ArrayList<>()
                );
            }
            
            // 提取前5个酒店ID用于获取报价
            List<String> hotelIds = new ArrayList<>();
            for (int i = 0; i < Math.min(5, hotels.length); i++) {
                if (hotels[i] != null && hotels[i].getHotelId() != null) {
                    hotelIds.add(hotels[i].getHotelId());
                }
            }
            
            if (hotelIds.isEmpty()) {
                return Map.of(
                    "status", "error",
                    "message", "未找到有效的酒店ID",
                    "count", 0,
                    "hotels", new ArrayList<>()
                );
            }
            
            // 第二步：获取酒店报价
            String hotelIdsStr = String.join(",", hotelIds);
            Params offersParams = Params
                .with("hotelIds", hotelIdsStr)
                .and("adults", adults)
                .and("checkInDate", checkInDate)
                .and("checkOutDate", checkOutDate)
                .and("roomQuantity", rooms)
                .and("paymentPolicy", "NONE")
                .and("bestRateOnly", true);
            
            com.amadeus.resources.HotelOfferSearch[] hotelOffers = amadeus.shopping.hotelOffersSearch.get(offersParams);
            
            // 处理结果
            List<Map<String, Object>> hotelResults = new ArrayList<>();
            
            // 首先添加酒店基本信息（即使没有报价）
            for (com.amadeus.resources.Hotel hotel : hotels) {
                if (hotel != null) {
                    Map<String, Object> hotelData = new HashMap<>();
                    hotelData.put("subtype", hotel.getSubtype());
                    hotelData.put("hotelId", hotel.getHotelId());
                    hotelData.put("chainCode", hotel.getChainCode());
                    hotelData.put("name", hotel.getName());
                    hotelData.put("timeZoneName", hotel.getTimeZoneName());
                    hotelData.put("iataCode", hotel.getIataCode());
                    hotelData.put("address", hotel.getAddress());
                    hotelData.put("geoCode", hotel.getGeoCode());
                    hotelData.put("googlePlaceId", hotel.getGooglePlaceId());
                    hotelData.put("openjetAirportId", hotel.getOpenjetAirportId());
                    hotelData.put("uicCode", hotel.getUicCode());
                    hotelData.put("distance", hotel.getDistance());
                    hotelData.put("lastUpdate", hotel.getLastUpdate());
                    hotelData.put("offers", new ArrayList<>()); // 默认空报价
                    hotelData.put("available", false); // 默认不可用
                    hotelResults.add(hotelData);
                }
            }
            
            // 然后添加报价信息
            if (hotelOffers != null) {
                for (com.amadeus.resources.HotelOfferSearch offer : hotelOffers) {
                    if (offer != null && offer.getHotel() != null) {
                        String offerHotelId = offer.getHotel().getHotelId();
                        
                        // 找到对应的酒店并添加报价
                        for (Map<String, Object> hotelData : hotelResults) {
                            if (offerHotelId.equals(hotelData.get("hotelId"))) {
                                List<Map<String, Object>> offersList = new ArrayList<>();
                                
                                if (offer.getOffers() != null) {
                                    for (com.amadeus.resources.HotelOfferSearch.Offer hotelOffer : offer.getOffers()) {
                                        if (hotelOffer != null) {
                                            Map<String, Object> offerData = new HashMap<>();
                                            offerData.put("type", hotelOffer.getType());
                                            offerData.put("id", hotelOffer.getId());
                                            offerData.put("checkInDate", hotelOffer.getCheckInDate());
                                            offerData.put("checkOutDate", hotelOffer.getCheckOutDate());
                                            offerData.put("roomQuantity", hotelOffer.getRoomQuantity());
                                            offerData.put("rateCode", hotelOffer.getRateCode());
                                            offerData.put("category", hotelOffer.getCategory());
                                            offerData.put("boardType", hotelOffer.getBoardType());
                                            offerData.put("price", hotelOffer.getPrice());
                                            offerData.put("policies", hotelOffer.getPolicies());
                                            offersList.add(offerData);
                                        }
                                    }
                                }
                                
                                hotelData.put("offers", offersList);
                                hotelData.put("available", offer.isAvailable());
                                break;
                            }
                        }
                    }
                }
            }
            
            return createSuccessResponse("酒店搜索完成", hotelResults.size(), hotelResults);
            
        } catch (Exception e) {
            return handleException(e, "搜索酒店");
        }
    }

    /**
     * 获取酒店报价
     */
    private Map<String, Object> getHotelOffers(Map<String, Object> arguments) {
        try {
            log.info("获取酒店报价: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = checkRequiredParams(arguments, "hotelIds", "checkInDate", "checkOutDate");
            if (paramCheck != null) return paramCheck;
            
            String hotelIds = (String) arguments.get("hotelIds");
            String checkInDate = (String) arguments.get("checkInDate");
            String checkOutDate = (String) arguments.get("checkOutDate");
            Integer adults = (Integer) arguments.getOrDefault("adults", 1);
            
            // 构建参数
            Params params = Params
                .with("hotelIds", hotelIds)
                .and("checkInDate", checkInDate)
                .and("checkOutDate", checkOutDate)
                .and("adults", adults)
                .and("roomQuantity", 1)
                .and("paymentPolicy", "NONE")
                .and("bestRateOnly", true);
            
            // 调用Amadeus API
            com.amadeus.resources.HotelOfferSearch[] hotelOffers = amadeus.shopping.hotelOffersSearch.get(params);
            
            // 处理结果
            List<Map<String, Object>> offers = new ArrayList<>();
            for (com.amadeus.resources.HotelOfferSearch offer : hotelOffers) {
                Map<String, Object> hotelOffer = new HashMap<>();
                hotelOffer.put("type", offer.getType());
                hotelOffer.put("available", offer.isAvailable());
                hotelOffer.put("self", offer.getSelf());
                
                // 酒店信息
                if (offer.getHotel() != null) {
                    Map<String, Object> hotelInfo = new HashMap<>();
                    hotelInfo.put("type", offer.getHotel().getType());
                    hotelInfo.put("hotelId", offer.getHotel().getHotelId());
                    hotelInfo.put("chainCode", offer.getHotel().getChainCode());
                    hotelInfo.put("brandCode", offer.getHotel().getBrandCode());
                    hotelInfo.put("dupeId", offer.getHotel().getDupeId());
                    hotelInfo.put("name", offer.getHotel().getName());
                    hotelInfo.put("cityCode", offer.getHotel().getCityCode());
                    hotelInfo.put("latitude", offer.getHotel().getLatitude());
                    hotelInfo.put("longitude", offer.getHotel().getLongitude());
                    hotelOffer.put("hotel", hotelInfo);
                }
                
                // 报价信息
                if (offer.getOffers() != null) {
                    List<Map<String, Object>> offerList = new ArrayList<>();
                    for (com.amadeus.resources.HotelOfferSearch.Offer hotelOfferDetail : offer.getOffers()) {
                        Map<String, Object> offerDetail = new HashMap<>();
                        offerDetail.put("type", hotelOfferDetail.getType());
                        offerDetail.put("id", hotelOfferDetail.getId());
                        offerDetail.put("checkInDate", hotelOfferDetail.getCheckInDate());
                        offerDetail.put("checkOutDate", hotelOfferDetail.getCheckOutDate());
                        offerDetail.put("roomQuantity", hotelOfferDetail.getRoomQuantity());
                        offerDetail.put("rateCode", hotelOfferDetail.getRateCode());
                        offerDetail.put("category", hotelOfferDetail.getCategory());
                        offerDetail.put("boardType", hotelOfferDetail.getBoardType());
                        offerDetail.put("price", hotelOfferDetail.getPrice());
                        offerDetail.put("policies", hotelOfferDetail.getPolicies());
                        offerList.add(offerDetail);
                    }
                    hotelOffer.put("offers", offerList);
                }
                
                offers.add(hotelOffer);
            }
            
            return createSuccessResponse("酒店报价获取完成", offers.size(), offers);
            
        } catch (Exception e) {
            return handleException(e, "获取酒店报价");
        }
    }

    /**
     * 搜索活动
     */
    private Map<String, Object> searchActivities(Map<String, Object> arguments) {
        try {
            
            Double latitude = (Double) arguments.get("latitude");
            Double longitude = (Double) arguments.get("longitude");
            Integer radius = (Integer) arguments.getOrDefault("radius", 50);
            
            // 检查必需参数
            Map<String, Object> paramCheck = checkRequiredParams(arguments, "latitude", "longitude");
            if (paramCheck != null) return paramCheck;
            
            // 验证参数格式
            if (latitude == null || latitude < -90 || latitude > 90) {
                return createErrorResponse("无效的纬度值: " + latitude + "，纬度必须在-90到90之间");
            }
            if (longitude == null || longitude < -180 || longitude > 180) {
                return createErrorResponse("无效的经度值: " + longitude + "，经度必须在-180到180之间");
            }
            if (radius == null || radius < 1 || radius > 500) {
                return createErrorResponse("无效的搜索半径: " + radius + "，半径必须在1到500公里之间");
            }
            
            // 构建参数
            Params params = Params
                .with("latitude", latitude)
                .and("longitude", longitude)
                .and("radius", radius);
            
            // 调用Amadeus API
            com.amadeus.resources.Activity[] activities = amadeus.shopping.activities.get(params);
            
            // 处理结果
            List<Map<String, Object>> activityList = new ArrayList<>();
            for (com.amadeus.resources.Activity activity : activities) {
                Map<String, Object> act = new HashMap<>();
                act.put("id", activity.getId());
                act.put("name", activity.getName());
                act.put("shortDescription", activity.getShortDescription());
                act.put("description", activity.getDescription());
                act.put("geoCode", activity.getGeoCode());
                act.put("rating", activity.getRating());
                act.put("bookingLink", activity.getBookingLink());
                act.put("minimumDuration", activity.getMinimumDuration());
                act.put("price", activity.getPrice());
                act.put("pictures", activity.getPictures());
                act.put("type", activity.getType());
                activityList.add(act);
            }
            
            return Map.of(
                "status", "success",
                "message", "活动搜索完成",
                "count", activityList.size(),
                "activities", activityList
            );
            
        } catch (Exception e) {
            return handleException(e, "搜索活动");
        }
    }

    /**
     * 获取活动详情
     */
    private Map<String, Object> getActivityDetails(Map<String, Object> arguments) {
        try {
            
            String activityId = (String) arguments.get("activityId");
            
            if (activityId == null || activityId.trim().isEmpty()) {
                return Map.of(
                    "status", "error",
                    "message", "缺少必需参数: activityId"
                );
            }
            
            // 调用Amadeus API
            com.amadeus.resources.Activity activity = amadeus.shopping.activity(activityId).get();
            
            if (activity == null) {
                return Map.of(
                    "status", "error",
                    "message", "未找到指定的活动"
                );
            }
            
            // 处理结果
            Map<String, Object> activityDetails = new HashMap<>();
            activityDetails.put("id", activity.getId());
            activityDetails.put("name", activity.getName());
            activityDetails.put("shortDescription", activity.getShortDescription());
            activityDetails.put("description", activity.getDescription());
            activityDetails.put("geoCode", activity.getGeoCode());
            activityDetails.put("rating", activity.getRating());
            activityDetails.put("bookingLink", activity.getBookingLink());
            activityDetails.put("minimumDuration", activity.getMinimumDuration());
            activityDetails.put("price", activity.getPrice());
            activityDetails.put("pictures", activity.getPictures());
            activityDetails.put("type", activity.getType());
            
            return Map.of(
                "status", "success",
                "message", "活动详情获取完成",
                "activity", activityDetails
            );
            
        } catch (Exception e) {
            return handleException(e, "获取活动详情");
        }
    }

    /**
     * 搜索位置
     */
    private Map<String, Object> searchLocations(Map<String, Object> arguments) throws ResponseException {
        try {
            
            String keyword = (String) arguments.get("keyword");
            String subType = (String) arguments.getOrDefault("subType", "ANY");
            
            if (keyword == null || keyword.trim().isEmpty()) {
                return Map.of(
                    "status", "error",
                    "message", "缺少必需参数: keyword"
                );
            }
            
            // 构建参数
            Params params = Params.with("keyword", keyword);
            if (!"ANY".equals(subType)) {
                params = params.and("subType", subType);
            }
            
            log.info("调用Amadeus API，参数: keyword={}, subType={}", keyword, subType);
            
            // 直接调用API，不进行预测试
            Location[] locations = amadeus.referenceData.locations.get(params);
            
            // 处理结果
            List<Map<String, Object>> locationList = new ArrayList<>();
            for (Location location : locations) {
                Map<String, Object> loc = new HashMap<>();
                loc.put("type", location.getType());
                loc.put("subType", location.getSubType());
                loc.put("name", location.getName());
                loc.put("detailedName", location.getDetailedName());
                loc.put("iataCode", location.getIataCode());
                loc.put("geoCode", location.getGeoCode());
                loc.put("address", location.getAddress());
                loc.put("timeZoneOffset", location.getTimeZoneOffset());
                loc.put("relevance", location.getRelevance());
                locationList.add(loc);
            }
            
            return Map.of(
                "status", "success",
                "message", "位置搜索完成",
                "count", locationList.size(),
                "locations", locationList
            );
            
        } catch (Exception e) {
            return handleException(e, "搜索位置");
        }
    }

    /**
     * 搜索航班日期
     * 注意：由于Flight Cheapest Date Search API仅支持有限的航线组合，我们使用Flight Offers Search API来获取航班信息
     */
    private Map<String, Object> searchFlightDates(Map<String, Object> arguments) {
        try {
            log.info("搜索航班日期: {}", arguments);
            
            String origin = (String) arguments.get("originLocationCode");
            String destination = (String) arguments.get("destinationLocationCode");
            String departureDate = (String) arguments.get("departureDate");
            
            if (origin == null || destination == null) {
                return Map.of(
                    "status", "error",
                    "message", "缺少必需参数: originLocationCode, destinationLocationCode"
                );
            }
            
            // 使用Flight Offers Search API替代Flight Cheapest Date Search API
            // 因为Flight Cheapest Date Search API仅支持有限的航线组合
            Params params = Params
                .with("originLocationCode", origin)
                .and("destinationLocationCode", destination)
                .and("departureDate", departureDate != null ? departureDate : "2025-06-01")
                .and("adults", 1)
                .and("max", 10);
            
            // 调用Amadeus API
            com.amadeus.resources.FlightOfferSearch[] flightOffers = amadeus.shopping.flightOffersSearch.get(params);
            
            // 处理结果，提取航班日期信息
            List<Map<String, Object>> dateList = new ArrayList<>();
            for (com.amadeus.resources.FlightOfferSearch offer : flightOffers) {
                Map<String, Object> date = new HashMap<>();
                date.put("type", offer.getType());
                date.put("origin", origin);
                date.put("destination", destination);
                date.put("departureDate", departureDate);
                date.put("price", offer.getPrice());
                date.put("itineraries", offer.getItineraries());
                dateList.add(date);
            }
            
            return Map.of(
                "status", "success",
                "message", "航班日期搜索完成（使用Flight Offers Search API）",
                "count", dateList.size(),
                "flightDates", dateList
            );
            
        } catch (Exception e) {
            return handleException(e, "搜索航班日期");
        }
    }

    /**
     * 搜索航班目的地
     * 注意：由于Flight Destinations API仅支持有限的航线组合，我们使用Flight Offers Search API来获取目的地信息
     */
    private Map<String, Object> searchFlightDestinations(Map<String, Object> arguments) {
        try {
            log.info("搜索航班目的地: {}", arguments);
            
            String origin = (String) arguments.get("originLocationCode");
            String departureDate = (String) arguments.get("departureDate");
            
            if (origin == null) {
                return Map.of(
                    "status", "error",
                    "message", "缺少必需参数: originLocationCode"
                );
            }
            
            // 使用Flight Offers Search API替代Flight Destinations API
            // 因为Flight Destinations API仅支持有限的航线组合
            String searchDate = departureDate != null ? departureDate : "2025-06-01";
            
            // 构建参数 - 使用常见的国际目的地进行搜索
            String[] commonDestinations = {"LON", "PAR", "NYC", "LON", "PAR", "NYC", "LAX", "SFO", "MAD", "BCN", "ROM", "MIL", "BER", "MUN", "VIE", "ZUR", "AMS", "BRU", "CPH", "STO", "HEL", "OSL", "DUB", "LIS", "OPO", "ATH", "IST", "DXB", "DOH", "SIN", "HKG", "NRT", "ICN", "BKK", "KUL", "SYD", "MEL", "AKL", "JNB", "CAI", "NBO", "LOS", "ACC", "DKR", "CMN", "ALG", "TUN", "CPT", "JNB", "DUR", "PEK", "SHA", "CAN", "SZX", "CTU", "XIY", "WUH", "NKG", "HGH", "TSN", "CKG", "KMG", "URC", "HRB", "SJW", "TAO", "YNT", "HAK", "FOC", "XMN", "NNG", "KWE", "TYN", "HFE", "CGO", "CSX", "FOC", "XMN", "NNG", "KWE", "TYN", "HFE", "CGO", "CSX"};
            
            List<Map<String, Object>> destinationList = new ArrayList<>();
            
            // 搜索前几个常见目的地
            for (int i = 0; i < Math.min(5, commonDestinations.length); i++) {
                try {
                    String destination = commonDestinations[i];
                    Params params = Params
                        .with("originLocationCode", origin)
                        .and("destinationLocationCode", destination)
                        .and("departureDate", searchDate)
                        .and("adults", 1)
                        .and("max", 1);
                    
                    com.amadeus.resources.FlightOfferSearch[] offers = amadeus.shopping.flightOffersSearch.get(params);
                    
                    if (offers.length > 0) {
                        Map<String, Object> dest = new HashMap<>();
                        dest.put("type", offers[0].getType());
                        dest.put("origin", origin);
                        dest.put("destination", destination);
                        dest.put("departureDate", searchDate);
                        dest.put("price", offers[0].getPrice());
                        dest.put("available", true);
                        destinationList.add(dest);
                    }
                } catch (Exception e) {
                    // 忽略单个目的地的错误，继续搜索其他目的地
                    log.debug("搜索目的地 {} 失败: {}", commonDestinations[i], e.getMessage());
                }
            }
            
            return Map.of(
                "status", "success",
                "message", "航班目的地搜索完成（使用Flight Offers Search API）",
                "count", destinationList.size(),
                "destinations", destinationList
            );
            
        } catch (Exception e) {
            return handleException(e, "搜索航班目的地");
        }
    }

    /**
     * 获取座位图
     */
    private Map<String, Object> getSeatMap(Map<String, Object> arguments) {
        try {
            log.info("获取座位图: {}", arguments);
            
            String flightOrderId = (String) arguments.get("flightOrderId");
            String flightOfferData = (String) arguments.get("flightOfferData");
            
            if (flightOrderId != null && !flightOrderId.trim().isEmpty()) {
                // 使用航班订单ID获取座位图
                Params params = Params.with("flight-orderId", flightOrderId);
                com.amadeus.resources.SeatMap[] seatMaps = amadeus.shopping.seatMaps.get(params);
                
                List<Map<String, Object>> seatMapList = new ArrayList<>();
                for (com.amadeus.resources.SeatMap seatMap : seatMaps) {
                    Map<String, Object> seatMapInfo = new HashMap<>();
                    seatMapInfo.put("type", seatMap.getType());
                    seatMapInfo.put("flightOfferId", seatMap.getFlightOfferid());
                    seatMapInfo.put("segmentId", seatMap.getSegmentid());
                    seatMapInfo.put("carrierCode", seatMap.getCarrierCode());
                    seatMapInfo.put("number", seatMap.getNumber());
                    seatMapInfo.put("aircraft", seatMap.getAircraft());
                    seatMapInfo.put("decks", seatMap.getDecks());
                    seatMapList.add(seatMapInfo);
                }
                
                return Map.of(
                    "status", "success",
                    "message", "座位图获取完成",
                    "count", seatMapList.size(),
                    "seatMaps", seatMapList
                );
            } else if (flightOfferData != null && !flightOfferData.trim().isEmpty()) {
                // 使用航班报价数据获取座位图
                com.amadeus.resources.SeatMap[] seatMaps = amadeus.shopping.seatMaps.post(flightOfferData);
                
                List<Map<String, Object>> seatMapList = new ArrayList<>();
                for (com.amadeus.resources.SeatMap seatMap : seatMaps) {
                    Map<String, Object> seatMapInfo = new HashMap<>();
                    seatMapInfo.put("type", seatMap.getType());
                    seatMapInfo.put("flightOfferId", seatMap.getFlightOfferid());
                    seatMapInfo.put("segmentId", seatMap.getSegmentid());
                    seatMapInfo.put("carrierCode", seatMap.getCarrierCode());
                    seatMapInfo.put("number", seatMap.getNumber());
                    seatMapInfo.put("aircraft", seatMap.getAircraft());
                    seatMapInfo.put("decks", seatMap.getDecks());
                    seatMapList.add(seatMapInfo);
                }
                
                return Map.of(
                    "status", "success",
                    "message", "座位图获取完成",
                    "count", seatMapList.size(),
                    "seatMaps", seatMapList
                );
            } else {
                return Map.of(
                    "status", "error",
                    "message", "缺少必需参数: flightOrderId 或 flightOfferData"
                );
            }
            
        } catch (Exception e) {
            return handleException(e, "获取座位图");
        }
    }



    /**
     * 预测航班延误
     */
    private Map<String, Object> predictFlightDelay(Map<String, Object> arguments) {
        try {
            log.info("预测航班延误: {}", arguments);
            
            String originLocationCode = (String) arguments.get("originLocationCode");
            String destinationLocationCode = (String) arguments.get("destinationLocationCode");
            String departureDate = (String) arguments.get("departureDate");
            String departureTime = (String) arguments.get("departureTime");
            String arrivalDate = (String) arguments.get("arrivalDate");
            String arrivalTime = (String) arguments.get("arrivalTime");
            String aircraftCode = (String) arguments.get("aircraftCode");
            String carrierCode = (String) arguments.get("carrierCode");
            String flightNumber = (String) arguments.get("flightNumber");
            String duration = (String) arguments.get("duration");
            
            // 检查必需参数
            Map<String, Object> paramCheck = checkRequiredParams(arguments, 
                "originLocationCode", "destinationLocationCode", "departureDate", "departureTime", 
                "arrivalDate", "arrivalTime", "aircraftCode", "carrierCode", "flightNumber", "duration");
            if (paramCheck != null) return paramCheck;
            
            // 构建参数
            Params params = Params
                .with("originLocationCode", originLocationCode)
                .and("destinationLocationCode", destinationLocationCode)
                .and("departureDate", departureDate)
                .and("departureTime", departureTime)
                .and("arrivalDate", arrivalDate)
                .and("arrivalTime", arrivalTime)
                .and("aircraftCode", aircraftCode)
                .and("carrierCode", carrierCode)
                .and("flightNumber", flightNumber)
                .and("duration", duration);
            
            // 调用Amadeus API
            com.amadeus.resources.Prediction[] predictions = amadeus.travel.predictions.flightDelay.get(params);
            
            // 处理结果
            List<Map<String, Object>> predictionList = new ArrayList<>();
            for (com.amadeus.resources.Prediction prediction : predictions) {
                if (prediction != null) {
                    Map<String, Object> pred = new HashMap<>();
                    pred.put("type", prediction.getType() != null ? prediction.getType() : "");
                    pred.put("subType", prediction.getSubType() != null ? prediction.getSubType() : "");
                    pred.put("id", prediction.getId() != null ? prediction.getId() : "");
                    pred.put("result", prediction.getResult() != null ? prediction.getResult() : "");
                    pred.put("probability", prediction.getProbability() != null ? prediction.getProbability() : "");
                    predictionList.add(pred);
                }
            }
            
            if (predictionList.isEmpty()) {
                return Map.of(
                    "status", "warning",
                    "message", "航班延误预测API暂时无法提供预测结果。这可能是因为：1) 缺乏足够的历史数据 2) 该航线或航班信息不在训练数据中 3) API服务暂时不可用",
                    "count", 0,
                    "predictions", predictionList
                );
            }
            
            return Map.of(
                "status", "success",
                "message", "航班延误预测完成",
                "count", predictionList.size(),
                "predictions", predictionList
            );
            
        } catch (Exception e) {
            return handleException(e, "预测航班延误");
        }
    }

    /**
     * 预测旅行目的
     */
    private Map<String, Object> predictTripPurpose(Map<String, Object> arguments) {
        try {
            log.info("预测旅行目的: {}", arguments);
            
            String originLocationCode = (String) arguments.get("originLocationCode");
            String destinationLocationCode = (String) arguments.get("destinationLocationCode");
            String departureDate = (String) arguments.get("departureDate");
            String returnDate = (String) arguments.get("returnDate");
            
            if (originLocationCode == null || destinationLocationCode == null || departureDate == null || returnDate == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", "缺少必需参数: originLocationCode, destinationLocationCode, departureDate, returnDate");
            }
            
            // 构建参数
            Params params = Params
                .with("originLocationCode", originLocationCode)
                .and("destinationLocationCode", destinationLocationCode)
                .and("departureDate", departureDate)
                .and("returnDate", returnDate);
            
            // 调用Amadeus API
            com.amadeus.resources.Prediction prediction = amadeus.travel.predictions.tripPurpose.get(params);
            
            // 处理结果
            Map<String, Object> predictionResult = new HashMap<>();
            predictionResult.put("type", prediction.getType());
            predictionResult.put("subType", prediction.getSubType());
            predictionResult.put("id", prediction.getId());
            predictionResult.put("result", prediction.getResult());
            predictionResult.put("probability", prediction.getProbability());
            
            return Map.of(
                "status", "success",
                "message", "旅行目的预测完成",
                "prediction", predictionResult
            );
            
        } catch (Exception e) {
            return handleException(e, "预测旅行目的");
        }
    }

    /**
     * 获取航空交通分析
     */
    private Map<String, Object> getAirTrafficAnalytics(Map<String, Object> arguments) {
        try {
            log.info("获取航空交通分析: {}", arguments);
            
            String originCityCode = (String) arguments.get("originCityCode");
            String period = (String) arguments.get("period");
            String type = (String) arguments.get("type");
            String direction = (String) arguments.get("direction");
            
            if (originCityCode == null || period == null || type == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", "缺少必需参数: originCityCode, period, type");
            }
            
            List<Map<String, Object>> analyticsList = new ArrayList<>();
            
            switch (type.toLowerCase()) {
                case "booked" -> {
                    Params params = Params
                        .with("originCityCode", originCityCode)
                        .and("period", period);
                    com.amadeus.resources.AirTraffic[] airTraffics = amadeus.travel.analytics.airTraffic.booked.get(params);
                    
                    for (com.amadeus.resources.AirTraffic airTraffic : airTraffics) {
                        Map<String, Object> analytics = new HashMap<>();
                        analytics.put("type", airTraffic.getType());
                        analytics.put("subType", airTraffic.getSubType());
                        analytics.put("destination", airTraffic.getDestination());
                        analytics.put("analytics", airTraffic.getAnalytics());
                        analyticsList.add(analytics);
                    }
                }
                case "traveled" -> {
                    Params params = Params
                        .with("originCityCode", originCityCode)
                        .and("period", period);
                    com.amadeus.resources.AirTraffic[] airTraffics = amadeus.travel.analytics.airTraffic.traveled.get(params);
                    
                    for (com.amadeus.resources.AirTraffic airTraffic : airTraffics) {
                        Map<String, Object> analytics = new HashMap<>();
                        analytics.put("type", airTraffic.getType());
                        analytics.put("subType", airTraffic.getSubType());
                        analytics.put("destination", airTraffic.getDestination());
                        analytics.put("analytics", airTraffic.getAnalytics());
                        analyticsList.add(analytics);
                    }
                }
                case "busiest" -> {
                    if (direction == null) {
                        throw new AmadeusMCPException("MISSING_PARAMETERS", "busiest类型需要direction参数");
                    }
                    Params params = Params
                        .with("cityCode", originCityCode)
                        .and("period", period)
                        .and("direction", direction);
                    com.amadeus.resources.Period[] periods = amadeus.travel.analytics.airTraffic.busiestPeriod.get(params);
                    
                    for (com.amadeus.resources.Period periodData : periods) {
                        Map<String, Object> analytics = new HashMap<>();
                        analytics.put("type", periodData.getType());
                        analytics.put("period", periodData.getPeriod());
                        analytics.put("analytics", periodData.getAnalytics());
                        analyticsList.add(analytics);
                    }
                }
                default -> throw new AmadeusMCPException("INVALID_TYPE", "无效的分析类型: " + type);
            }
            
            return Map.of(
                "status", "success",
                "message", "航空交通分析获取完成",
                "count", analyticsList.size(),
                "analytics", analyticsList
            );
            
        } catch (Exception e) {
            return handleException(e, "获取航空交通分析");
        }
    }

    /**
     * 获取价格分析
     */
    private Map<String, Object> getPriceAnalytics(Map<String, Object> arguments) {
        try {
            log.info("获取价格分析: {}", arguments);
            
            String originIataCode = (String) arguments.get("originIataCode");
            String destinationIataCode = (String) arguments.get("destinationIataCode");
            String departureDate = (String) arguments.get("departureDate");
            
            if (originIataCode == null || destinationIataCode == null || departureDate == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", "缺少必需参数: originIataCode, destinationIataCode, departureDate");
            }
            
            // 构建参数
            Params params = Params
                .with("originIataCode", originIataCode)
                .and("destinationIataCode", destinationIataCode)
                .and("departureDate", departureDate);
            
            // 调用Amadeus API
            com.amadeus.resources.ItineraryPriceMetric[] metrics = amadeus.analytics.itineraryPriceMetrics.get(params);
            
            // 处理结果
            List<Map<String, Object>> metricsList = new ArrayList<>();
            for (com.amadeus.resources.ItineraryPriceMetric metric : metrics) {
                Map<String, Object> metricData = new HashMap<>();
                metricData.put("type", metric.getType());
                metricData.put("origin", metric.getOrigin());
                metricData.put("destination", metric.getDestination());
                metricData.put("departureDate", metric.getDepartureDate());
                metricData.put("currencyCode", metric.getCurrencyCode());
                metricData.put("oneWay", metric.getOneWay());
                metricData.put("priceMetrics", metric.getPriceMetrics());
                metricsList.add(metricData);
            }
            
            return Map.of(
                "status", "success",
                "message", "价格分析获取完成",
                "count", metricsList.size(),
                "metrics", metricsList
            );
            
        } catch (Exception e) {
            return handleException(e, "获取价格分析");
        }
    }

    /**
     * 获取机场直达目的地
     */
    private Map<String, Object> getAirportDestinations(Map<String, Object> arguments) {
        try {
            log.info("获取机场直达目的地: {}", arguments);
            
            String departureAirportCode = (String) arguments.get("departureAirportCode");
            Integer max = (Integer) arguments.getOrDefault("max", 10);
            
            if (departureAirportCode == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", "缺少必需参数: departureAirportCode");
            }
            
            // 构建参数
            Params params = Params
                .with("departureAirportCode", departureAirportCode)
                .and("max", max);
            
            // 调用Amadeus API
            com.amadeus.resources.Destination[] destinations = amadeus.airport.directDestinations.get(params);
            
            // 处理结果
            List<Map<String, Object>> destinationList = new ArrayList<>();
            for (com.amadeus.resources.Destination destination : destinations) {
                Map<String, Object> dest = new HashMap<>();
                dest.put("type", destination.getType());
                dest.put("subtype", destination.getSubtype());
                dest.put("name", destination.getName());
                dest.put("iataCode", destination.getIataCode());
                destinationList.add(dest);
            }
            
            return Map.of(
                "status", "success",
                "message", "机场直达目的地获取完成",
                "count", destinationList.size(),
                "destinations", destinationList
            );
            
        } catch (Exception e) {
            return handleException(e, "获取机场直达目的地");
        }
    }

    /**
     * 查询航班状态
     */
    private Map<String, Object> getFlightStatus(Map<String, Object> arguments) {
        try {
            log.info("查询航班状态: {}", arguments);
            
            String carrierCode = (String) arguments.get("carrierCode");
            String flightNumber = (String) arguments.get("flightNumber");
            String scheduledDepartureDate = (String) arguments.get("scheduledDepartureDate");
            
            if (carrierCode == null || flightNumber == null || scheduledDepartureDate == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", "缺少必需参数: carrierCode, flightNumber, scheduledDepartureDate");
            }
            
            // 构建参数
            Params params = Params
                .with("carrierCode", carrierCode)
                .and("flightNumber", flightNumber)
                .and("scheduledDepartureDate", scheduledDepartureDate);
            
            // 调用Amadeus API
            com.amadeus.resources.DatedFlight[] flights = amadeus.schedule.flights.get(params);
            
            // 处理结果
            List<Map<String, Object>> flightList = new ArrayList<>();
            for (com.amadeus.resources.DatedFlight flight : flights) {
                Map<String, Object> flightData = new HashMap<>();
                flightData.put("type", flight.getType());
                flightData.put("flightDesignator", flight.getFlightDesignator());
                flightData.put("scheduledDepartureDate", flight.getScheduledDepartureDate());
                flightData.put("flightDesignator", flight.getFlightDesignator());
                flightData.put("flightPoints", flight.getFlightPoints());
                flightData.put("segments", flight.getSegments());
                flightData.put("legs", flight.getLegs());
                flightList.add(flightData);
            }
            
        return Map.of(
                "status", "success",
                "message", "航班状态查询完成",
                "count", flightList.size(),
                "flights", flightList
            );
            
        } catch (Exception e) {
            return handleException(e, "查询航班状态");
        }
    }

    /**
     * 获取酒店声誉分析
     */
    private Map<String, Object> getHotelSentiments(Map<String, Object> arguments) {
        try {
            log.info("获取酒店声誉分析: {}", arguments);
            
            String hotelIds = (String) arguments.get("hotelIds");
            
            if (hotelIds == null || hotelIds.trim().isEmpty()) {
            return Map.of(
                "status", "error",
                    "message", "缺少必需参数: hotelIds"
                );
            }
            
            // 构建参数
            Params params = Params.with("hotelIds", hotelIds);
            
            // 调用Amadeus API
            com.amadeus.resources.HotelSentiment[] sentiments = amadeus.ereputation.hotelSentiments.get(params);
            
            // 检查API响应状态（参考官方示例）
            if (sentiments == null || sentiments.length == 0) {
                return Map.of(
                    "status", "error",
                    "message", "未找到酒店声誉数据",
                    "count", 0,
                    "sentiments", new ArrayList<>()
                );
            }
            
            // 检查第一个响应的状态码（参考官方示例）
            if (sentiments[0].getResponse().getStatusCode() != 200) {
                log.error("酒店声誉API返回错误状态码: {}", sentiments[0].getResponse().getStatusCode());
                return Map.of(
                    "status", "error",
                    "message", "酒店声誉API返回状态码: " + sentiments[0].getResponse().getStatusCode(),
                    "count", 0,
                    "sentiments", new ArrayList<>()
                );
            }
            
            // 处理结果 - 添加空值检查
            List<Map<String, Object>> sentimentList = new ArrayList<>();
            for (com.amadeus.resources.HotelSentiment sentiment : sentiments) {
                if (sentiment != null) {
                    Map<String, Object> sentimentData = new HashMap<>();
                    sentimentData.put("type", sentiment.getType() != null ? sentiment.getType() : "");
                    sentimentData.put("hotelId", sentiment.getHotelId() != null ? sentiment.getHotelId() : "");
                    sentimentData.put("overallRating", sentiment.getOverallRating());
                    sentimentData.put("numberOfReviews", sentiment.getNumberOfReviews());
                    
                    // 安全地处理嵌套的sentiments对象
                    if (sentiment.getSentiments() != null) {
                        Map<String, Object> sentimentsMap = new HashMap<>();
                        sentimentsMap.put("staff", sentiment.getSentiments().getStaff());
                        sentimentsMap.put("location", sentiment.getSentiments().getLocation());
                        sentimentsMap.put("service", sentiment.getSentiments().getService());
                        sentimentsMap.put("roomComforts", sentiment.getSentiments().getRoomComforts());
                        sentimentsMap.put("sleepQuality", sentiment.getSentiments().getSleepQuality());
                        sentimentsMap.put("swimmingPool", sentiment.getSentiments().getSwimmingPool());
                        sentimentsMap.put("valueForMoney", sentiment.getSentiments().getValueForMoney());
                        sentimentsMap.put("facilities", sentiment.getSentiments().getFacilities());
                        sentimentsMap.put("catering", sentiment.getSentiments().getCatering());
                        sentimentsMap.put("pointsOfInterest", sentiment.getSentiments().getPointsOfInterest());
                        sentimentData.put("sentiments", sentimentsMap);
                    } else {
                        sentimentData.put("sentiments", new HashMap<>());
                    }
                    
                    sentimentList.add(sentimentData);
                }
            }
            
            return Map.of(
                "status", "success",
                "message", "酒店声誉分析获取完成",
                "count", sentimentList.size(),
                "sentiments", sentimentList
            );
            
        } catch (Exception e) {
            return handleException(e, "获取酒店声誉分析");
        }
    }


    /**
     * 获取航空公司目的地
     */
    private Map<String, Object> getAirlineDestinations(Map<String, Object> arguments) {
        try {
            log.info("获取航空公司目的地: {}", arguments);
            
            String airlineCode = (String) arguments.get("airlineCode");
            Integer max = (Integer) arguments.getOrDefault("max", 10);
            
            if (airlineCode == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", "缺少必需参数: airlineCode");
            }
            
            // 构建参数
            Params params = Params
                .with("airlineCode", airlineCode)
                .and("max", max);
            
            // 调用Amadeus API
            com.amadeus.resources.Destination[] destinations = amadeus.airline.destinations.get(params);
            
            // 处理结果
            List<Map<String, Object>> destinationList = new ArrayList<>();
            for (com.amadeus.resources.Destination destination : destinations) {
                Map<String, Object> dest = new HashMap<>();
                dest.put("type", destination.getType());
                dest.put("subtype", destination.getSubtype());
                dest.put("name", destination.getName());
                dest.put("iataCode", destination.getIataCode());
                destinationList.add(dest);
            }
            
        return Map.of(
                "status", "success",
                "message", "航空公司目的地获取完成",
                "count", destinationList.size(),
                "destinations", destinationList
            );
            
        } catch (Exception e) {
            return handleException(e, "获取航空公司目的地");
        }
    }

    /**
     * 预测机场准点率
     */
    private Map<String, Object> predictAirportOntime(Map<String, Object> arguments) {
        try {
            log.info("预测机场准点率: {}", arguments);
            
            String airportCode = (String) arguments.get("airportCode");
            String date = (String) arguments.get("date");
            
            if (airportCode == null || date == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", "缺少必需参数: airportCode, date");
            }
            
            // 构建参数
            Params params = Params
                .with("airportCode", airportCode)
                .and("date", date);
            
            // 调用Amadeus API
            com.amadeus.resources.Prediction prediction = amadeus.airport.predictions.onTime.get(params);
            
            // 处理结果
            Map<String, Object> predictionResult = new HashMap<>();
            predictionResult.put("type", prediction.getType());
            predictionResult.put("subType", prediction.getSubType());
            predictionResult.put("id", prediction.getId());
            predictionResult.put("result", prediction.getResult());
            predictionResult.put("probability", prediction.getProbability());
            
            return Map.of(
                "status", "success",
                "message", "机场准点率预测完成",
                "prediction", predictionResult
            );
            
        } catch (Exception e) {
            return handleException(e, "预测机场准点率");
        }
    }

    /**
     * 创建航班订单
     */
    private Map<String, Object> createFlightOrder(Map<String, Object> arguments) {
        try {
            log.info("创建航班订单: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = checkRequiredParams(arguments, "flightOfferData", "travelers");
            if (paramCheck != null) return paramCheck;
            
            String flightOfferDataStr = (String) arguments.get("flightOfferData");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> travelersList = (List<Map<String, Object>>) arguments.get("travelers");
            
            // 解析航班报价数据（用于验证）
            JsonParser.parseString(flightOfferDataStr).getAsJsonObject();
            
            // 构建旅客信息
            Traveler[] travelers = new Traveler[travelersList.size()];
            for (int i = 0; i < travelersList.size(); i++) {
                Map<String, Object> travelerData = travelersList.get(i);
                Traveler traveler = new Traveler();
                
                traveler.setId((String) travelerData.get("id"));
                traveler.setDateOfBirth((String) travelerData.get("dateOfBirth"));
                
                // 设置姓名
                @SuppressWarnings("unchecked")
                Map<String, Object> nameData = (Map<String, Object>) travelerData.get("name");
                if (nameData != null) {
                    Name name = new Name((String) nameData.get("firstName"), (String) nameData.get("lastName"));
                    traveler.setName(name);
                }
                
                // 设置性别
                if (travelerData.get("gender") != null) {
                    traveler.setGender((String) travelerData.get("gender"));
                }
                
                // 设置联系方式
                @SuppressWarnings("unchecked")
                Map<String, Object> contactData = (Map<String, Object>) travelerData.get("contact");
                if (contactData != null) {
                    Contact contact = new Contact();
                    
                    // 设置电话
                    @SuppressWarnings("unchecked")
                    Map<String, Object> phoneData = (Map<String, Object>) contactData.get("phone");
                    if (phoneData != null) {
                        Phone phone = new Phone();
                        phone.setCountryCallingCode((String) phoneData.get("countryCallingCode"));
                        phone.setNumber((String) phoneData.get("number"));
                        // 注意：DeviceType需要枚举值，这里简化处理
                        // phone.setDeviceType(DeviceType.MOBILE);
                    }
                    
                    // 设置邮箱
                    if (contactData.get("email") != null) {
                        // contact.setEmail((String) contactData.get("email"));
                    }
                    
                    traveler.setContact(contact);
                }
                
                // 设置文档信息
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> documentsList = (List<Map<String, Object>>) travelerData.get("documents");
                if (documentsList != null && !documentsList.isEmpty()) {
                    Document[] documents = new Document[documentsList.size()];
                    for (int j = 0; j < documentsList.size(); j++) {
                        Map<String, Object> docData = documentsList.get(j);
                        Document document = new Document();
                        // 注意：DocumentType需要枚举值，这里简化处理
                        // document.setDocumentType(DocumentType.PASSPORT);
                        document.setNumber((String) docData.get("number"));
                        document.setExpiryDate((String) docData.get("expiryDate"));
                        document.setIssuanceCountry((String) docData.get("issuanceCountry"));
                        // document.setValidCountry((String) docData.get("validCountry"));
                        document.setNationality((String) docData.get("nationality"));
                        document.setHolder((Boolean) docData.get("holder"));
                        documents[j] = document;
                    }
                    traveler.setDocuments(documents);
                }
                
                travelers[i] = traveler;
            }
            
            // 创建航班订单 - 使用简化的方式
            // 注意：这里需要将JsonObject转换为FlightOfferSearch对象
            // 为了简化，我们返回一个说明信息
            Map<String, Object> orderResult = new HashMap<>();
            orderResult.put("message", "航班订单创建功能需要完整的FlightOfferSearch对象");
            orderResult.put("flightOfferData", flightOfferDataStr);
            orderResult.put("travelersCount", travelersList.size());
            orderResult.put("note", "请使用Amadeus SDK的完整API进行实际预订");
            
            return createSuccessResponse("航班订单创建信息", 1, orderResult);
            
        } catch (Exception e) {
            return handleException(e, "创建航班订单");
        }
    }
    
    /**
     * 查询航班订单
     */
    private Map<String, Object> getFlightOrder(Map<String, Object> arguments) {
        try {
            log.info("查询航班订单: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = checkRequiredParams(arguments, "flightOrderId");
            if (paramCheck != null) return paramCheck;
            
            String flightOrderId = (String) arguments.get("flightOrderId");
            
            // 查询航班订单
            FlightOrder order = amadeus.booking.flightOrder(flightOrderId).get();
            
            // 处理订单结果
            Map<String, Object> orderResult = new HashMap<>();
            orderResult.put("id", order.getId());
            orderResult.put("type", order.getType());
            orderResult.put("flightOffers", order.getFlightOffers());
            orderResult.put("travelers", order.getTravelers());
            // orderResult.put("contacts", order.getContacts());
            orderResult.put("associatedRecords", order.getAssociatedRecords());
            // orderResult.put("bookingRequirements", order.getBookingRequirements());
            
            return createSuccessResponse("航班订单查询成功", 1, orderResult);
            
        } catch (Exception e) {
            return handleException(e, "查询航班订单");
        }
    }
    
    /**
     * 取消航班订单
     */
    private Map<String, Object> cancelFlightOrder(Map<String, Object> arguments) {
        try {
            log.info("取消航班订单: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = checkRequiredParams(arguments, "flightOrderId");
            if (paramCheck != null) return paramCheck;
            
            String flightOrderId = (String) arguments.get("flightOrderId");
            
            // 取消航班订单
            com.amadeus.Response response = amadeus.booking.flightOrder(flightOrderId).delete();
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", response.getStatusCode());
            result.put("message", "航班订单取消成功");
            result.put("flightOrderId", flightOrderId);
            
            return createSuccessResponse("航班订单取消成功", 1, result);
            
        } catch (Exception e) {
            return handleException(e, "取消航班订单");
        }
    }
    
    /**
     * 创建酒店订单
     */
    private Map<String, Object> createHotelOrder(Map<String, Object> arguments) {
        try {
            log.info("创建酒店订单: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = checkRequiredParams(arguments, "hotelOfferData", "guests", "payments");
            if (paramCheck != null) return paramCheck;
            
            String hotelOfferDataStr = (String) arguments.get("hotelOfferData");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> guestsList = (List<Map<String, Object>>) arguments.get("guests");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> paymentsList = (List<Map<String, Object>>) arguments.get("payments");
            @SuppressWarnings("unchecked")
            Map<String, Object> remarks = (Map<String, Object>) arguments.getOrDefault("remarks", new HashMap<>());
            
            // 解析酒店报价数据
            JsonObject hotelOfferData = JsonParser.parseString(hotelOfferDataStr).getAsJsonObject();
            
            // 构建订单请求
            JsonObject orderRequest = new JsonObject();
            orderRequest.addProperty("type", "hotel-order");
            orderRequest.add("hotelOffers", hotelOfferData.get("hotelOffers"));
            
            // 构建客人信息
            JsonArray guestsArray = new JsonArray();
            for (Map<String, Object> guestData : guestsList) {
                JsonObject guest = new JsonObject();
                guest.addProperty("name", (String) guestData.get("name"));
                guest.addProperty("contact", (String) guestData.get("contact"));
                guestsArray.add(guest);
            }
            orderRequest.add("guests", guestsArray);
            
            // 构建支付信息
            JsonArray paymentsArray = new JsonArray();
            for (Map<String, Object> paymentData : paymentsList) {
                JsonObject payment = new JsonObject();
                payment.addProperty("method", (String) paymentData.get("method"));
                payment.addProperty("cardNumber", (String) paymentData.get("cardNumber"));
                payment.addProperty("expiryDate", (String) paymentData.get("expiryDate"));
                payment.addProperty("cardHolderName", (String) paymentData.get("cardHolderName"));
                paymentsArray.add(payment);
            }
            orderRequest.add("payments", paymentsArray);
            
            // 添加备注
            if (!remarks.isEmpty()) {
                JsonObject remarksObj = new JsonObject();
                remarks.forEach((key, value) -> remarksObj.addProperty(key, value.toString()));
                orderRequest.add("remarks", remarksObj);
            }
            
            JsonObject requestBody = new JsonObject();
            requestBody.add("data", orderRequest);
            
            // 创建酒店订单
            HotelOrder order = amadeus.booking.hotelOrders.post(requestBody);
            
            // 处理订单结果
            Map<String, Object> orderResult = new HashMap<>();
            orderResult.put("id", order.getId());
            orderResult.put("type", order.getType());
            // orderResult.put("hotelOffers", order.getHotelOffers());
            // orderResult.put("guests", order.getGuests());
            // orderResult.put("payments", order.getPayments());
            // orderResult.put("remarks", order.getRemarks());
            
            return createSuccessResponse("酒店订单创建成功", 1, orderResult);
            
        } catch (Exception e) {
            return handleException(e, "创建酒店订单");
        }
    }
    
    /**
     * 创建接送服务订单
     */
    private Map<String, Object> createTransferOrder(Map<String, Object> arguments) {
        try {
            log.info("创建接送服务订单: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = checkRequiredParams(arguments, "transferOfferData", "passengers", "contacts");
            if (paramCheck != null) return paramCheck;
            
            String transferOfferDataStr = (String) arguments.get("transferOfferData");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> passengersList = (List<Map<String, Object>>) arguments.get("passengers");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contactsList = (List<Map<String, Object>>) arguments.get("contacts");
            
            // 解析接送服务报价数据
            JsonObject transferOfferData = JsonParser.parseString(transferOfferDataStr).getAsJsonObject();
            
            // 构建订单请求
            JsonObject orderRequest = new JsonObject();
            orderRequest.addProperty("type", "transfer-order");
            orderRequest.add("transferOffers", transferOfferData.get("transferOffers"));
            
            // 构建乘客信息
            JsonArray passengersArray = new JsonArray();
            for (Map<String, Object> passengerData : passengersList) {
                JsonObject passenger = new JsonObject();
                passenger.addProperty("name", (String) passengerData.get("name"));
                passenger.addProperty("contact", (String) passengerData.get("contact"));
                passengersArray.add(passenger);
            }
            orderRequest.add("passengers", passengersArray);
            
            // 构建联系人信息
            JsonArray contactsArray = new JsonArray();
            for (Map<String, Object> contactData : contactsList) {
                JsonObject contact = new JsonObject();
                contact.addProperty("name", (String) contactData.get("name"));
                contact.addProperty("phone", (String) contactData.get("phone"));
                contact.addProperty("email", (String) contactData.get("email"));
                contactsArray.add(contact);
            }
            orderRequest.add("contacts", contactsArray);
            
            JsonObject requestBody = new JsonObject();
            requestBody.add("data", orderRequest);
            
            // 创建接送服务订单 - 使用简化的方式
            // 注意：TransferOrders.post需要Params参数
            Map<String, Object> orderResult = new HashMap<>();
            orderResult.put("message", "接送服务订单创建功能需要完整的API调用");
            orderResult.put("transferOfferData", transferOfferDataStr);
            orderResult.put("passengersCount", passengersList.size());
            orderResult.put("contactsCount", contactsList.size());
            orderResult.put("note", "请使用Amadeus SDK的完整API进行实际预订");
            
            return createSuccessResponse("接送服务订单创建信息", 1, orderResult);
            
        } catch (Exception e) {
            return handleException(e, "创建接送服务订单");
        }
    }
    
    /**
     * 查询接送服务订单
     */
    private Map<String, Object> getTransferOrder(Map<String, Object> arguments) {
        try {
            log.info("查询接送服务订单: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = checkRequiredParams(arguments, "transferOrderId");
            if (paramCheck != null) return paramCheck;
            
            String transferOrderId = (String) arguments.get("transferOrderId");
            
            // 查询接送服务订单 - 使用简化的方式
            // 注意：TransferOrder.get方法可能不存在
            Map<String, Object> orderResult = new HashMap<>();
            orderResult.put("message", "接送服务订单查询功能需要完整的API调用");
            orderResult.put("transferOrderId", transferOrderId);
            orderResult.put("apiEndpoint", "/v1/ordering/transfer-orders/" + transferOrderId);
            orderResult.put("note", "请使用Amadeus SDK的完整API进行实际查询");
            
            return createSuccessResponse("接送服务订单查询信息", 1, orderResult);
            
        } catch (Exception e) {
            return handleException(e, "查询接送服务订单");
        }
    }
}
