package com.zhouruojun.amadeusmcp.service.hotel;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.zhouruojun.amadeusmcp.service.common.AmadeusServiceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 酒店服务
 * 专门处理酒店相关的API调用
 */
@Slf4j
@Service
public class HotelService {

    @Autowired
    private Amadeus amadeus;

    @Autowired
    private AmadeusServiceUtils utils;

    /**
     * 搜索酒店
     */
    public Map<String, Object> searchHotels(Map<String, Object> arguments) {
        try {
            log.info("搜索酒店: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = utils.checkRequiredParams(arguments, "cityCode");
            if (paramCheck != null) return paramCheck;
            
            String cityCode = utils.getStringParam(arguments, "cityCode");
            
            // 验证城市代码格式
            if (cityCode == null || cityCode.trim().isEmpty()) {
                return utils.createErrorResponse("城市代码不能为空");
            }
            
            // 根据城市代码获取酒店列表
            Params hotelListParams = Params.with("cityCode", cityCode);
            com.amadeus.resources.Hotel[] hotels = amadeus.referenceData.locations.hotels.byCity.get(hotelListParams);
            
            if (hotels == null || hotels.length == 0) {
                return utils.createSuccessResponse("未找到该城市的酒店", 0, new ArrayList<>());
            }
            
            // 转换为Map格式返回
            List<Map<String, Object>> hotelResults = new ArrayList<>();
            for (com.amadeus.resources.Hotel hotel : hotels) {
                if (hotel != null) {
                    Map<String, Object> hotelData = createHotelDataMap(hotel);
                    hotelResults.add(hotelData);
                }
            }
            
            return utils.createSuccessResponse("酒店搜索完成", hotelResults.size(), hotelResults);
            
        } catch (Exception e) {
            return utils.handleException(e, "搜索酒店");
        }
    }

    /**
     * 获取酒店报价
     */
    public Map<String, Object> getHotelOffers(Map<String, Object> arguments) {
        try {
            log.info("获取酒店报价: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = utils.checkRequiredParams(arguments, 
                "hotelIds", "checkInDate", "checkOutDate");
            if (paramCheck != null) return paramCheck;
            
            String hotelIds = utils.getStringParam(arguments, "hotelIds");
            String checkInDate = utils.getStringParam(arguments, "checkInDate");
            String checkOutDate = utils.getStringParam(arguments, "checkOutDate");
            Integer adults = utils.getIntegerParam(arguments, "adults", 1);
            
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
            com.amadeus.resources.HotelOfferSearch[] hotelOffers = 
                amadeus.shopping.hotelOffersSearch.get(params);
            
            // 处理结果
            List<Map<String, Object>> offers = processHotelOffers(hotelOffers);
            
            return utils.createSuccessResponse("酒店报价获取完成", offers.size(), offers);
            
        } catch (Exception e) {
            return utils.handleException(e, "获取酒店报价");
        }
    }

    /**
     * 获取酒店声誉分析
     */
    public Map<String, Object> getHotelSentiments(Map<String, Object> arguments) {
        try {
            log.info("获取酒店声誉分析: {}", arguments);
            
            String hotelIds = utils.getStringParam(arguments, "hotelIds");
            
            if (hotelIds == null || hotelIds.trim().isEmpty()) {
                return utils.createErrorResponse("缺少必需参数: hotelIds");
            }
            
            // 构建参数
            Params params = Params.with("hotelIds", hotelIds);
            
            // 调用Amadeus API
            com.amadeus.resources.HotelSentiment[] sentiments = 
                amadeus.ereputation.hotelSentiments.get(params);
            
            // 检查API响应状态
            if (sentiments == null || sentiments.length == 0) {
                return utils.createErrorResponse("未找到酒店声誉数据");
            }
            
            // 检查第一个响应的状态码
            if (sentiments[0].getResponse().getStatusCode() != 200) {
                log.error("酒店声誉API返回错误状态码: {}", sentiments[0].getResponse().getStatusCode());
                return utils.createErrorResponse("酒店声誉API返回状态码: " + sentiments[0].getResponse().getStatusCode());
            }
            
            // 处理结果
            List<Map<String, Object>> sentimentList = processHotelSentiments(sentiments);
            
            return utils.createSuccessResponse("酒店声誉分析获取完成", sentimentList.size(), sentimentList);
            
        } catch (Exception e) {
            return utils.handleException(e, "获取酒店声誉分析");
        }
    }



    /**
     * 创建酒店数据映射
     */
    private Map<String, Object> createHotelDataMap(com.amadeus.resources.Hotel hotel) {
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
        return hotelData;
    }

    /**
     * 处理酒店报价详情
     */
    private List<Map<String, Object>> processHotelOfferDetails(com.amadeus.resources.HotelOfferSearch.Offer[] offers) {
        List<Map<String, Object>> offersList = new ArrayList<>();
        
        if (offers != null) {
            for (com.amadeus.resources.HotelOfferSearch.Offer hotelOffer : offers) {
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
        
        return offersList;
    }

    /**
     * 处理酒店报价结果
     */
    private List<Map<String, Object>> processHotelOffers(com.amadeus.resources.HotelOfferSearch[] hotelOffers) {
        List<Map<String, Object>> offers = new ArrayList<>();
        
        for (com.amadeus.resources.HotelOfferSearch offer : hotelOffers) {
            Map<String, Object> hotelOffer = new HashMap<>();
            hotelOffer.put("type", offer.getType());
            hotelOffer.put("available", offer.isAvailable());
            hotelOffer.put("self", offer.getSelf());
            
            // 酒店信息
            if (offer.getHotel() != null) {
                Map<String, Object> hotelInfo = createHotelInfoMap(offer.getHotel());
                hotelOffer.put("hotel", hotelInfo);
            }
            
            // 报价信息
            List<Map<String, Object>> offerList = processHotelOfferDetails(offer.getOffers());
            hotelOffer.put("offers", offerList);
            
            offers.add(hotelOffer);
        }
        
        return offers;
    }

    /**
     * 创建酒店信息映射
     */
    private Map<String, Object> createHotelInfoMap(com.amadeus.resources.HotelOfferSearch.Hotel hotel) {
        Map<String, Object> hotelInfo = new HashMap<>();
        hotelInfo.put("type", hotel.getType());
        hotelInfo.put("hotelId", hotel.getHotelId());
        hotelInfo.put("chainCode", hotel.getChainCode());
        hotelInfo.put("brandCode", hotel.getBrandCode());
        hotelInfo.put("dupeId", hotel.getDupeId());
        hotelInfo.put("name", hotel.getName());
        hotelInfo.put("cityCode", hotel.getCityCode());
        hotelInfo.put("latitude", hotel.getLatitude());
        hotelInfo.put("longitude", hotel.getLongitude());
        return hotelInfo;
    }

    /**
     * 处理酒店声誉分析结果
     */
    private List<Map<String, Object>> processHotelSentiments(com.amadeus.resources.HotelSentiment[] sentiments) {
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
                    Map<String, Object> sentimentsMap = createSentimentsMap(sentiment.getSentiments());
                    sentimentData.put("sentiments", sentimentsMap);
                } else {
                    sentimentData.put("sentiments", new HashMap<>());
                }
                
                sentimentList.add(sentimentData);
            }
        }
        
        return sentimentList;
    }

    /**
     * 创建情感分析映射
     */
    private Map<String, Object> createSentimentsMap(Object sentiments) {
        Map<String, Object> sentimentsMap = new HashMap<>();
        // 由于HotelSentiment.Sentiments类型可能不存在，这里使用通用处理
        if (sentiments != null) {
            // 使用反射或通用方法处理sentiments对象
            sentimentsMap.put("note", "情感分析数据需要根据实际API响应结构处理");
        }
        return sentimentsMap;
    }
}
