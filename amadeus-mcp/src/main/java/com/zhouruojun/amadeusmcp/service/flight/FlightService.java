package com.zhouruojun.amadeusmcp.service.flight;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.zhouruojun.amadeusmcp.service.common.AmadeusServiceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 航班服务
 * 专门处理航班相关的API调用
 */
@Slf4j
@Service
public class FlightService {

    @Autowired
    private Amadeus amadeus;

    @Autowired
    private AmadeusServiceUtils utils;


    /**
     * 搜索航班
     */
    public Map<String, Object> searchFlights(Map<String, Object> arguments) {
        try {
            log.info("搜索航班: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = utils.checkRequiredParams(arguments, 
                "originLocationCode", "destinationLocationCode", "departureDate");
            if (paramCheck != null) return paramCheck;
            
            // 验证参数
            Map<String, Object> validationResult = utils.validateFlightSearchParams(arguments);
            if (validationResult != null) return validationResult;
            
            String origin = utils.getStringParam(arguments, "originLocationCode");
            String destination = utils.getStringParam(arguments, "destinationLocationCode");
            String departureDate = utils.getStringParam(arguments, "departureDate");
            Integer adults = utils.getIntegerParam(arguments, "adults", 1);
            
            // 使用GET方法搜索航班报价
            Params params = Params
                .with("originLocationCode", origin)
                .and("destinationLocationCode", destination)
                .and("departureDate", departureDate)
                .and("adults", adults)
                .and("max", 10);
            
            // 调用Amadeus API
            com.amadeus.resources.FlightOfferSearch[] flightOffers = 
                amadeus.shopping.flightOffersSearch.get(params);
            
            // 处理结果
            List<Map<String, Object>> offers = processFlightOffers(flightOffers);
            
            return utils.createSuccessResponse("航班搜索完成", offers.size(), offers);
            
        } catch (Exception e) {
            return utils.handleException(e, "搜索航班");
        }
    }

    /**
     * 搜索航班日期
     */
    public Map<String, Object> searchFlightDates(Map<String, Object> arguments) {
        try {
            log.info("搜索航班日期: {}", arguments);
            
            String origin = utils.getStringParam(arguments, "originLocationCode");
            String destination = utils.getStringParam(arguments, "destinationLocationCode");
            String departureDate = utils.getStringParam(arguments, "departureDate");
            
            if (origin == null || destination == null) {
                return utils.createErrorResponse("缺少必需参数: originLocationCode, destinationLocationCode");
            }
            
            // 使用Flight Offers Search API替代Flight Cheapest Date Search API
            Params params = Params
                .with("originLocationCode", origin)
                .and("destinationLocationCode", destination)
                .and("departureDate", departureDate != null ? departureDate : "2025-06-01")
                .and("adults", 1)
                .and("max", 10);
            
            // 调用Amadeus API
            com.amadeus.resources.FlightOfferSearch[] flightOffers = 
                amadeus.shopping.flightOffersSearch.get(params);
            
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
            
            return utils.createSuccessResponse("航班日期搜索完成", dateList.size(), dateList);
            
        } catch (Exception e) {
            return utils.handleException(e, "搜索航班日期");
        }
    }

    /**
     * 搜索航班目的地
     */
    public Map<String, Object> searchFlightDestinations(Map<String, Object> arguments) {
        try {
            log.info("搜索航班目的地: {}", arguments);
            
            String origin = utils.getStringParam(arguments, "originLocationCode");
            String departureDate = utils.getStringParam(arguments, "departureDate");
            
            if (origin == null) {
                return utils.createErrorResponse("缺少必需参数: originLocationCode");
            }
            
            // 使用常见的国际目的地进行搜索
            String[] commonDestinations = {
                "LON", "PAR", "NYC", "LAX", "SFO", "MAD", "BCN", "ROM", "MIL", 
                "BER", "MUN", "VIE", "ZUR", "AMS", "BRU", "CPH", "STO", "HEL", 
                "OSL", "DUB", "LIS", "OPO", "ATH", "IST", "DXB", "DOH", "SIN", 
                "HKG", "NRT", "ICN", "BKK", "KUL", "SYD", "MEL", "AKL"
            };
            
            String searchDate = departureDate != null ? departureDate : "2025-06-01";
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
                    
                    com.amadeus.resources.FlightOfferSearch[] offers = 
                        amadeus.shopping.flightOffersSearch.get(params);
                    
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
            
            return utils.createSuccessResponse("航班目的地搜索完成", destinationList.size(), destinationList);
            
        } catch (Exception e) {
            return utils.handleException(e, "搜索航班目的地");
        }
    }

    /**
     * 通过订单ID获取座位图
     */
    public Map<String, Object> getSeatMapByOrder(Map<String, Object> arguments) {
        try {
            log.info("通过订单ID获取座位图: {}", arguments);
            
            String flightOrderId = utils.getStringParam(arguments, "flightOrderId");
            
            if (flightOrderId == null || flightOrderId.trim().isEmpty()) {
                return utils.createErrorResponse("缺少必需参数: flightOrderId");
            }
            
            // 直接调用Amadeus API
            Params params = Params.with("flight-orderId", flightOrderId);
            com.amadeus.resources.SeatMap[] seatMaps = amadeus.shopping.seatMaps.get(params);
            
            List<Map<String, Object>> seatMapList = processSeatMaps(seatMaps);
            return utils.createSuccessResponse("座位图获取完成", seatMapList.size(), seatMapList);
            
        } catch (Exception e) {
            return utils.handleException(e, "通过订单ID获取座位图");
        }
    }

    /**
     * 通过报价数据获取座位图
     */
    public Map<String, Object> getSeatMapByOffer(Map<String, Object> arguments) {
        try {
            log.info("通过报价数据获取座位图: {}", arguments);
            
            String flightOfferData = utils.getStringParam(arguments, "flightOfferData");
            
            if (flightOfferData == null || flightOfferData.trim().isEmpty()) {
                return utils.createErrorResponse("缺少必需参数: flightOfferData");
            }
            
            // 直接调用Amadeus API
            com.amadeus.resources.SeatMap[] seatMaps = amadeus.shopping.seatMaps.post(flightOfferData);
            
            List<Map<String, Object>> seatMapList = processSeatMaps(seatMaps);
            return utils.createSuccessResponse("座位图获取完成", seatMapList.size(), seatMapList);
            
        } catch (Exception e) {
            return utils.handleException(e, "通过报价数据获取座位图");
        }
    }

    /**
     * 查询航班状态
     */
    public Map<String, Object> getFlightStatus(Map<String, Object> arguments) {
        try {
            log.info("查询航班状态: {}", arguments);
            
            String carrierCode = utils.getStringParam(arguments, "carrierCode");
            String flightNumber = utils.getStringParam(arguments, "flightNumber");
            String scheduledDepartureDate = utils.getStringParam(arguments, "scheduledDepartureDate");
            
            if (carrierCode == null || flightNumber == null || scheduledDepartureDate == null) {
                return utils.createErrorResponse("缺少必需参数: carrierCode, flightNumber, scheduledDepartureDate");
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
                flightData.put("flightPoints", flight.getFlightPoints());
                flightData.put("segments", flight.getSegments());
                flightData.put("legs", flight.getLegs());
                flightList.add(flightData);
            }
            
            return utils.createSuccessResponse("航班状态查询完成", flightList.size(), flightList);
            
        } catch (Exception e) {
            return utils.handleException(e, "查询航班状态");
        }
    }


    /**
     * 处理航班报价结果
     */
    private List<Map<String, Object>> processFlightOffers(com.amadeus.resources.FlightOfferSearch[] flightOffers) {
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
        return flights;
    }


    /**
     * 处理座位图结果
     */
    private List<Map<String, Object>> processSeatMaps(com.amadeus.resources.SeatMap[] seatMaps) {
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
        return seatMapList;
    }
}
