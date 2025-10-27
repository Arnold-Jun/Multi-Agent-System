package com.zhouruojun.amadeusmcp.service.activity;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.zhouruojun.amadeusmcp.service.common.AmadeusServiceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 活动服务
 * 专门处理目的地活动和体验相关的API调用
 */
@Slf4j
@Service
public class ActivityService {

    @Autowired
    private Amadeus amadeus;

    @Autowired
    private AmadeusServiceUtils utils;

    /**
     * 搜索目的地活动
     */
    public Map<String, Object> searchActivities(Map<String, Object> arguments) {
        try {
            log.info("搜索活动: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = utils.checkRequiredParams(arguments, "latitude", "longitude");
            if (paramCheck != null) return paramCheck;
            
            // 验证参数
            Map<String, Object> validationResult = utils.validateActivitySearchParams(arguments);
            if (validationResult != null) return validationResult;
            
            Double latitude = utils.getDoubleParam(arguments, "latitude");
            Double longitude = utils.getDoubleParam(arguments, "longitude");
            Integer radius = utils.getIntegerParam(arguments, "radius", 50);
            
            // 构建参数
            Params params = Params
                .with("latitude", latitude)
                .and("longitude", longitude)
                .and("radius", radius);
            
            // 调用Amadeus API
            com.amadeus.resources.Activity[] activities = amadeus.shopping.activities.get(params);
            
            // 处理结果
            List<Map<String, Object>> activityList = processActivities(activities);
            
            return utils.createSuccessResponse("活动搜索完成", activityList.size(), activityList);
            
        } catch (Exception e) {
            return utils.handleException(e, "搜索活动");
        }
    }

    /**
     * 获取活动详情
     */
    public Map<String, Object> getActivityDetails(Map<String, Object> arguments) {
        try {
            log.info("获取活动详情: {}", arguments);
            
            String activityId = utils.getStringParam(arguments, "activityId");
            
            if (activityId == null || activityId.trim().isEmpty()) {
                return utils.createErrorResponse("缺少必需参数: activityId");
            }
            
            // 调用Amadeus API
            com.amadeus.resources.Activity activity = amadeus.shopping.activity(activityId).get();
            
            if (activity == null) {
                return utils.createErrorResponse("未找到指定的活动");
            }
            
            // 处理结果
            Map<String, Object> activityDetails = createActivityDetailsMap(activity);
            
            return Map.of(
                "status", "success",
                "message", "活动详情获取完成",
                "activity", activityDetails
            );
            
        } catch (Exception e) {
            return utils.handleException(e, "获取活动详情");
        }
    }

    /**
     * 处理活动列表结果
     */
    private List<Map<String, Object>> processActivities(com.amadeus.resources.Activity[] activities) {
        List<Map<String, Object>> activityList = new ArrayList<>();
        
        for (com.amadeus.resources.Activity activity : activities) {
            Map<String, Object> act = createActivityMap(activity);
            activityList.add(act);
        }
        
        return activityList;
    }

    /**
     * 创建活动映射
     */
    private Map<String, Object> createActivityMap(com.amadeus.resources.Activity activity) {
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
        return act;
    }

    /**
     * 创建活动详情映射
     */
    private Map<String, Object> createActivityDetailsMap(com.amadeus.resources.Activity activity) {
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
        return activityDetails;
    }
}
