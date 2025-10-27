package com.zhouruojun.amadeusmcp.service;

import com.amadeus.exceptions.ResponseException;
import com.zhouruojun.amadeusmcp.service.activity.ActivityService;
import com.zhouruojun.amadeusmcp.service.analytics.AnalyticsService;
import com.zhouruojun.amadeusmcp.service.booking.BookingService;
import com.zhouruojun.amadeusmcp.service.flight.FlightService;
import com.zhouruojun.amadeusmcp.service.hotel.HotelService;
import com.zhouruojun.amadeusmcp.service.tool.AmadeusToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Amadeus工具服务门面
 * 作为各个专门服务的统一入口，提供简洁的API
 */
@Slf4j
@Service
public class AmadeusToolService {

    @Autowired
    private AmadeusToolRegistry toolRegistry;
    
    @Autowired
    private FlightService flightService;
    
    @Autowired
    private HotelService hotelService;
    
    @Autowired
    private ActivityService activityService;
    
    @Autowired
    private AnalyticsService analyticsService;
    
    @Autowired
    private BookingService bookingService;

    /**
     * 获取可用的工具列表
     */
    public List<Map<String, Object>> getAvailableTools() {
        return toolRegistry.getAllTools();
    }

    /**
     * 调用工具
     */
    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) throws ResponseException {
        log.info("调用Amadeus工具: {} 参数: {}", toolName, arguments);
        
        return switch (toolName) {
            // 航班相关工具
            case "search_flights" -> flightService.searchFlights(arguments);
            case "search_flight_dates" -> flightService.searchFlightDates(arguments);
            case "search_flight_destinations" -> flightService.searchFlightDestinations(arguments);
            case "get_seat_map_by_order" -> flightService.getSeatMapByOrder(arguments);
            case "get_seat_map_by_offer" -> flightService.getSeatMapByOffer(arguments);
            case "get_flight_status" -> flightService.getFlightStatus(arguments);
            
            // 酒店相关工具
            case "search_hotels" -> hotelService.searchHotels(arguments);
            case "get_hotel_offers" -> hotelService.getHotelOffers(arguments);
            case "get_hotel_sentiments" -> hotelService.getHotelSentiments(arguments);
            
            // 活动相关工具
            case "search_activities" -> activityService.searchActivities(arguments);
            case "get_activity_details" -> activityService.getActivityDetails(arguments);
            
            // 分析相关工具
            case "search_locations" -> analyticsService.searchLocations(arguments);
            case "predict_flight_delay" -> analyticsService.predictFlightDelay(arguments);
            case "predict_trip_purpose" -> analyticsService.predictTripPurpose(arguments);
            case "get_booked_air_traffic" -> analyticsService.getBookedAirTraffic(arguments);
            case "get_traveled_air_traffic" -> analyticsService.getTraveledAirTraffic(arguments);
            case "get_busiest_air_traffic" -> analyticsService.getBusiestAirTraffic(arguments);
            case "get_price_analytics" -> analyticsService.getPriceAnalytics(arguments);
            case "get_airport_destinations" -> analyticsService.getAirportDestinations(arguments);
            case "get_airline_destinations" -> analyticsService.getAirlineDestinations(arguments);
            case "predict_airport_ontime" -> analyticsService.predictAirportOntime(arguments);
            
            // 预订相关工具
            case "create_flight_order" -> bookingService.createFlightOrder(arguments);
            case "get_flight_order" -> bookingService.getFlightOrder(arguments);
            case "cancel_flight_order" -> bookingService.cancelFlightOrder(arguments);
            case "create_hotel_order" -> bookingService.createHotelOrder(arguments);
            case "create_transfer_order" -> bookingService.createTransferOrder(arguments);
            case "get_transfer_order" -> bookingService.getTransferOrder(arguments);
            
            default -> throw new IllegalArgumentException("未知的工具: " + toolName);
        };
    }
}