package com.zhouruojun.amadeusmcp.service.analytics;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.exceptions.ResponseException;
import com.amadeus.resources.Location;
import com.amadeus.referencedata.Locations;
import com.zhouruojun.amadeusmcp.exception.AmadeusMCPException;
import com.zhouruojun.amadeusmcp.service.common.AmadeusServiceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 分析服务
 * 专门处理预测、分析和位置相关的API调用
 */
@Slf4j
@Service
public class AnalyticsService {

    @Autowired
    private Amadeus amadeus;

    @Autowired
    private AmadeusServiceUtils utils;

    /**
     * 搜索位置
     */
    public Map<String, Object> searchLocations(Map<String, Object> arguments) throws ResponseException {
        try {
            log.info("搜索位置: {}", arguments);
            
            String keyword = utils.getStringParam(arguments, "keyword");
            String subType = utils.getStringParam(arguments, "subType");
            
            if (keyword == null || keyword.trim().isEmpty()) {
                return utils.createErrorResponse("缺少必需参数: keyword");
            }
            
            // 构建参数
            Params params = Params.with("keyword", keyword);
            
            // 使用Map映射优化subType处理
            Map<String, Object> subTypeMap = Map.of(
                "CITY", Locations.CITY,
                "AIRPORT", Locations.AIRPORT
            );
            Object locationType = subTypeMap.getOrDefault(subType, Locations.ANY);
            params = params.and("subType", locationType);

            log.info("调用Amadeus API，参数: keyword={}, subType={}", keyword, subType);
            
            Location[] locations = amadeus.referenceData.locations.get(params);
            
            // 处理结果
            List<Map<String, Object>> locationList = processLocations(locations);
            
            return utils.createSuccessResponse("位置搜索完成", locationList.size(), locationList);
            
        } catch (Exception e) {
            return utils.handleException(e, "搜索位置");
        }
    }

    /**
     * 预测航班延误
     */
    public Map<String, Object> predictFlightDelay(Map<String, Object> arguments) {
        try {
            log.info("预测航班延误: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = utils.checkRequiredParams(arguments, 
                "originLocationCode", "destinationLocationCode", "departureDate", "departureTime", 
                "arrivalDate", "arrivalTime", "aircraftCode", "carrierCode", "flightNumber", "duration");
            if (paramCheck != null) return paramCheck;
            
            // 构建参数
            Params params = buildFlightDelayParams(arguments);
            
            // 调用Amadeus API
            com.amadeus.resources.Prediction[] predictions = 
                amadeus.travel.predictions.flightDelay.get(params);
            
            // 处理结果
            List<Map<String, Object>> predictionList = processPredictions(predictions);
            
            if (predictionList.isEmpty()) {
                return utils.createWarningResponse(
                    "航班延误预测API暂时无法提供预测结果。这可能是因为：1) 缺乏足够的历史数据 2) 该航线或航班信息不在训练数据中 3) API服务暂时不可用",
                    0, predictionList);
            }
            
            return utils.createSuccessResponse("航班延误预测完成", predictionList.size(), predictionList);
            
        } catch (Exception e) {
            return utils.handleException(e, "预测航班延误");
        }
    }

    /**
     * 预测旅行目的
     */
    public Map<String, Object> predictTripPurpose(Map<String, Object> arguments) {
        try {
            log.info("预测旅行目的: {}", arguments);
            
            String originLocationCode = utils.getStringParam(arguments, "originLocationCode");
            String destinationLocationCode = utils.getStringParam(arguments, "destinationLocationCode");
            String departureDate = utils.getStringParam(arguments, "departureDate");
            String returnDate = utils.getStringParam(arguments, "returnDate");
            
            if (originLocationCode == null || destinationLocationCode == null || 
                departureDate == null || returnDate == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", 
                    "缺少必需参数: originLocationCode, destinationLocationCode, departureDate, returnDate");
            }
            
            // 构建参数
            Params params = Params
                .with("originLocationCode", originLocationCode)
                .and("destinationLocationCode", destinationLocationCode)
                .and("departureDate", departureDate)
                .and("returnDate", returnDate);
            
            // 调用Amadeus API
            com.amadeus.resources.Prediction prediction = 
                amadeus.travel.predictions.tripPurpose.get(params);
            
            // 处理结果
            Map<String, Object> predictionResult = createPredictionMap(prediction);
            
            return Map.of(
                "status", "success",
                "message", "旅行目的预测完成",
                "prediction", predictionResult
            );
            
        } catch (Exception e) {
            return utils.handleException(e, "预测旅行目的");
        }
    }

    /**
     * 获取预订航空交通分析
     */
    public Map<String, Object> getBookedAirTraffic(Map<String, Object> arguments) {
        try {
            log.info("获取预订航空交通分析: {}", arguments);
            
            String originCityCode = utils.getStringParam(arguments, "originCityCode");
            String period = utils.getStringParam(arguments, "period");
            
            if (originCityCode == null || period == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", 
                    "缺少必需参数: originCityCode, period");
            }
            
            // 直接调用Amadeus API
            Params params = Params
                .with("originCityCode", originCityCode)
                .and("period", period);
            com.amadeus.resources.AirTraffic[] airTraffics = 
                amadeus.travel.analytics.airTraffic.booked.get(params);
            
            List<Map<String, Object>> analyticsList = processAirTraffic(airTraffics);
            
            return utils.createSuccessResponse("预订航空交通分析获取完成", analyticsList.size(), analyticsList);
            
        } catch (Exception e) {
            return utils.handleException(e, "获取预订航空交通分析");
        }
    }

    /**
     * 获取旅行航空交通分析
     */
    public Map<String, Object> getTraveledAirTraffic(Map<String, Object> arguments) {
        try {
            log.info("获取旅行航空交通分析: {}", arguments);
            
            String originCityCode = utils.getStringParam(arguments, "originCityCode");
            String period = utils.getStringParam(arguments, "period");
            
            if (originCityCode == null || period == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", 
                    "缺少必需参数: originCityCode, period");
            }
            
            // 直接调用Amadeus API
            Params params = Params
                .with("originCityCode", originCityCode)
                .and("period", period);
            com.amadeus.resources.AirTraffic[] airTraffics = 
                amadeus.travel.analytics.airTraffic.traveled.get(params);
            
            List<Map<String, Object>> analyticsList = processAirTraffic(airTraffics);
            
            return utils.createSuccessResponse("旅行航空交通分析获取完成", analyticsList.size(), analyticsList);
            
        } catch (Exception e) {
            return utils.handleException(e, "获取旅行航空交通分析");
        }
    }

    /**
     * 获取最繁忙航空交通分析
     */
    public Map<String, Object> getBusiestAirTraffic(Map<String, Object> arguments) {
        try {
            log.info("获取最繁忙航空交通分析: {}", arguments);
            
            String originCityCode = utils.getStringParam(arguments, "originCityCode");
            String period = utils.getStringParam(arguments, "period");
            String direction = utils.getStringParam(arguments, "direction");
            
            if (originCityCode == null || period == null || direction == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", 
                    "缺少必需参数: originCityCode, period, direction");
            }
            
            // 直接调用Amadeus API
            Params params = Params
                .with("cityCode", originCityCode)
                .and("period", period)
                .and("direction", direction);
            com.amadeus.resources.Period[] periods = 
                amadeus.travel.analytics.airTraffic.busiestPeriod.get(params);
            
            List<Map<String, Object>> analyticsList = processBusiestPeriods(periods);
            
            return utils.createSuccessResponse("最繁忙航空交通分析获取完成", analyticsList.size(), analyticsList);
            
        } catch (Exception e) {
            return utils.handleException(e, "获取最繁忙航空交通分析");
        }
    }

    /**
     * 获取价格分析
     */
    public Map<String, Object> getPriceAnalytics(Map<String, Object> arguments) {
        try {
            log.info("获取价格分析: {}", arguments);
            
            String originIataCode = utils.getStringParam(arguments, "originIataCode");
            String destinationIataCode = utils.getStringParam(arguments, "destinationIataCode");
            String departureDate = utils.getStringParam(arguments, "departureDate");
            
            if (originIataCode == null || destinationIataCode == null || departureDate == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", 
                    "缺少必需参数: originIataCode, destinationIataCode, departureDate");
            }
            
            // 构建参数
            Params params = Params
                .with("originIataCode", originIataCode)
                .and("destinationIataCode", destinationIataCode)
                .and("departureDate", departureDate);
            
            // 调用Amadeus API
            com.amadeus.resources.ItineraryPriceMetric[] metrics = 
                amadeus.analytics.itineraryPriceMetrics.get(params);
            
            // 处理结果
            List<Map<String, Object>> metricsList = processPriceMetrics(metrics);
            
            return utils.createSuccessResponse("价格分析获取完成", metricsList.size(), metricsList);
            
        } catch (Exception e) {
            return utils.handleException(e, "获取价格分析");
        }
    }

    /**
     * 获取机场直达目的地
     */
    public Map<String, Object> getAirportDestinations(Map<String, Object> arguments) {
        try {
            log.info("获取机场直达目的地: {}", arguments);
            
            String departureAirportCode = utils.getStringParam(arguments, "departureAirportCode");
            Integer max = utils.getIntegerParam(arguments, "max", 10);
            
            if (departureAirportCode == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", "缺少必需参数: departureAirportCode");
            }
            
            // 构建参数
            Params params = Params
                .with("departureAirportCode", departureAirportCode)
                .and("max", max);
            
            // 调用Amadeus API
            com.amadeus.resources.Destination[] destinations = 
                amadeus.airport.directDestinations.get(params);
            
            // 处理结果
            List<Map<String, Object>> destinationList = processDestinations(destinations);
            
            return utils.createSuccessResponse("机场直达目的地获取完成", destinationList.size(), destinationList);
            
        } catch (Exception e) {
            return utils.handleException(e, "获取机场直达目的地");
        }
    }

    /**
     * 获取航空公司目的地
     */
    public Map<String, Object> getAirlineDestinations(Map<String, Object> arguments) {
        try {
            log.info("获取航空公司目的地: {}", arguments);
            
            String airlineCode = utils.getStringParam(arguments, "airlineCode");
            Integer max = utils.getIntegerParam(arguments, "max", 10);
            
            if (airlineCode == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", "缺少必需参数: airlineCode");
            }
            
            // 构建参数
            Params params = Params
                .with("airlineCode", airlineCode)
                .and("max", max);
            
            // 调用Amadeus API
            com.amadeus.resources.Destination[] destinations = 
                amadeus.airline.destinations.get(params);
            
            // 处理结果
            List<Map<String, Object>> destinationList = processDestinations(destinations);
            
            return utils.createSuccessResponse("航空公司目的地获取完成", destinationList.size(), destinationList);
            
        } catch (Exception e) {
            return utils.handleException(e, "获取航空公司目的地");
        }
    }

    /**
     * 预测机场准点率
     */
    public Map<String, Object> predictAirportOntime(Map<String, Object> arguments) {
        try {
            log.info("预测机场准点率: {}", arguments);
            
            String airportCode = utils.getStringParam(arguments, "airportCode");
            String date = utils.getStringParam(arguments, "date");
            
            if (airportCode == null || date == null) {
                throw new AmadeusMCPException("MISSING_PARAMETERS", "缺少必需参数: airportCode, date");
            }
            
            // 构建参数
            Params params = Params
                .with("airportCode", airportCode)
                .and("date", date);
            
            // 调用Amadeus API
            com.amadeus.resources.Prediction prediction = 
                amadeus.airport.predictions.onTime.get(params);
            
            // 处理结果
            Map<String, Object> predictionResult = createPredictionMap(prediction);
            
            return Map.of(
                "status", "success",
                "message", "机场准点率预测完成",
                "prediction", predictionResult
            );
            
        } catch (Exception e) {
            return utils.handleException(e, "预测机场准点率");
        }
    }

    /**
     * 构建航班延误预测参数
     */
    private Params buildFlightDelayParams(Map<String, Object> arguments) {
        return Params
            .with("originLocationCode", utils.getStringParam(arguments, "originLocationCode"))
            .and("destinationLocationCode", utils.getStringParam(arguments, "destinationLocationCode"))
            .and("departureDate", utils.getStringParam(arguments, "departureDate"))
            .and("departureTime", utils.getStringParam(arguments, "departureTime"))
            .and("arrivalDate", utils.getStringParam(arguments, "arrivalDate"))
            .and("arrivalTime", utils.getStringParam(arguments, "arrivalTime"))
            .and("aircraftCode", utils.getStringParam(arguments, "aircraftCode"))
            .and("carrierCode", utils.getStringParam(arguments, "carrierCode"))
            .and("flightNumber", utils.getStringParam(arguments, "flightNumber"))
            .and("duration", utils.getStringParam(arguments, "duration"));
    }


    /**
     * 处理位置结果
     */
    private List<Map<String, Object>> processLocations(Location[] locations) {
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
        return locationList;
    }

    /**
     * 处理预测结果
     */
    private List<Map<String, Object>> processPredictions(com.amadeus.resources.Prediction[] predictions) {
        List<Map<String, Object>> predictionList = new ArrayList<>();
        for (com.amadeus.resources.Prediction prediction : predictions) {
            if (prediction != null) {
                Map<String, Object> pred = createPredictionMap(prediction);
                predictionList.add(pred);
            }
        }
        return predictionList;
    }

    /**
     * 创建预测映射
     */
    private Map<String, Object> createPredictionMap(com.amadeus.resources.Prediction prediction) {
        Map<String, Object> pred = new HashMap<>();
        pred.put("type", prediction.getType() != null ? prediction.getType() : "");
        pred.put("subType", prediction.getSubType() != null ? prediction.getSubType() : "");
        pred.put("id", prediction.getId() != null ? prediction.getId() : "");
        pred.put("result", prediction.getResult() != null ? prediction.getResult() : "");
        pred.put("probability", prediction.getProbability() != null ? prediction.getProbability() : "");
        return pred;
    }

    /**
     * 处理航空交通分析结果
     */
    private List<Map<String, Object>> processAirTraffic(com.amadeus.resources.AirTraffic[] airTraffics) {
        List<Map<String, Object>> analyticsList = new ArrayList<>();
        for (com.amadeus.resources.AirTraffic airTraffic : airTraffics) {
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("type", airTraffic.getType());
            analytics.put("subType", airTraffic.getSubType());
            analytics.put("destination", airTraffic.getDestination());
            analytics.put("analytics", airTraffic.getAnalytics());
            analyticsList.add(analytics);
        }
        return analyticsList;
    }

    /**
     * 处理最繁忙时段结果
     */
    private List<Map<String, Object>> processBusiestPeriods(com.amadeus.resources.Period[] periods) {
        List<Map<String, Object>> analyticsList = new ArrayList<>();
        for (com.amadeus.resources.Period periodData : periods) {
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("type", periodData.getType());
            analytics.put("period", periodData.getPeriod());
            analytics.put("analytics", periodData.getAnalytics());
            analyticsList.add(analytics);
        }
        return analyticsList;
    }

    /**
     * 处理价格指标结果
     */
    private List<Map<String, Object>> processPriceMetrics(com.amadeus.resources.ItineraryPriceMetric[] metrics) {
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
        return metricsList;
    }

    /**
     * 处理目的地结果
     */
    private List<Map<String, Object>> processDestinations(com.amadeus.resources.Destination[] destinations) {
        List<Map<String, Object>> destinationList = new ArrayList<>();
        for (com.amadeus.resources.Destination destination : destinations) {
            Map<String, Object> dest = new HashMap<>();
            dest.put("type", destination.getType());
            dest.put("subtype", destination.getSubtype());
            dest.put("name", destination.getName());
            dest.put("iataCode", destination.getIataCode());
            destinationList.add(dest);
        }
        return destinationList;
    }
}
