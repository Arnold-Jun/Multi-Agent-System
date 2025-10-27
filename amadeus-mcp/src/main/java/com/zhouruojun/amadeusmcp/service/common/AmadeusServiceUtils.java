package com.zhouruojun.amadeusmcp.service.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Amadeus服务通用工具类
 * 提供参数验证、响应构建等通用功能
 */
@Slf4j
@Component
public class AmadeusServiceUtils {

    /**
     * 检查必需参数
     */
    public Map<String, Object> checkRequiredParams(Map<String, Object> arguments, String... paramNames) {
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
    public boolean isValidDateFormat(String date) {
        if (date == null || date.trim().isEmpty()) {
            return false;
        }
        return date.matches("\\d{4}-\\d{2}-\\d{2}");
    }
    
    /**
     * 验证机场代码格式
     */
    public boolean isValidAirportCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        return code.matches("[A-Z]{3}");
    }

    /**
     * 验证坐标范围
     */
    public boolean isValidLatitude(Double latitude) {
        return latitude != null && latitude >= -90 && latitude <= 90;
    }

    /**
     * 验证坐标范围
     */
    public boolean isValidLongitude(Double longitude) {
        return longitude != null && longitude >= -180 && longitude <= 180;
    }

    /**
     * 验证搜索半径
     */
    public boolean isValidRadius(Integer radius) {
        return radius != null && radius >= 1 && radius <= 500;
    }

    /**
     * 创建成功响应
     */
    public Map<String, Object> createSuccessResponse(String message, int count, Object data) {
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
    public Map<String, Object> createErrorResponse(String message) {
        return Map.of(
            "status", "error",
            "message", message
        );
    }

    /**
     * 创建警告响应
     */
    public Map<String, Object> createWarningResponse(String message, int count, Object data) {
        return Map.of(
            "status", "warning",
            "message", message,
            "count", count,
            "data", data
        );
    }

    /**
     * 处理异常并返回错误响应
     */
    public Map<String, Object> handleException(Exception e, String operation) {
        log.error("{}失败", operation, e);
        return createErrorResponse(operation + "失败: " + e.getMessage());
    }

    /**
     * 验证航班搜索参数
     */
    public Map<String, Object> validateFlightSearchParams(Map<String, Object> arguments) {
        String origin = (String) arguments.get("originLocationCode");
        String destination = (String) arguments.get("destinationLocationCode");
        String departureDate = (String) arguments.get("departureDate");
        String returnDate = (String) arguments.get("returnDate");
        Integer adults = (Integer) arguments.getOrDefault("adults", 1);
        Integer children = (Integer) arguments.getOrDefault("children", 0);
        Integer infants = (Integer) arguments.getOrDefault("infants", 0);

        // 验证机场代码
        if (!isValidAirportCode(origin)) {
            return createErrorResponse("无效的出发地机场代码: " + origin);
        }
        if (!isValidAirportCode(destination)) {
            return createErrorResponse("无效的目的地机场代码: " + destination);
        }

        // 验证日期格式
        if (!isValidDateFormat(departureDate)) {
            return createErrorResponse("无效的出发日期格式: " + departureDate + "，请使用YYYY-MM-DD格式");
        }
        if (returnDate != null && !isValidDateFormat(returnDate)) {
            return createErrorResponse("无效的返程日期格式: " + returnDate + "，请使用YYYY-MM-DD格式");
        }

        // 验证旅客数量
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

        return null; // 验证通过
    }

    /**
     * 验证酒店搜索参数
     */
    public Map<String, Object> validateHotelSearchParams(Map<String, Object> arguments) {
        String checkInDate = (String) arguments.get("checkInDate");
        String checkOutDate = (String) arguments.get("checkOutDate");
        Integer adults = (Integer) arguments.getOrDefault("adults", 1);
        Integer rooms = (Integer) arguments.getOrDefault("rooms", 1);

        // 验证日期格式
        if (!isValidDateFormat(checkInDate)) {
            return createErrorResponse("无效的入住日期格式: " + checkInDate + "，请使用YYYY-MM-DD格式");
        }
        if (!isValidDateFormat(checkOutDate)) {
            return createErrorResponse("无效的退房日期格式: " + checkOutDate + "，请使用YYYY-MM-DD格式");
        }

        // 验证日期逻辑
        if (checkInDate.compareTo(checkOutDate) >= 0) {
            return createErrorResponse("退房日期必须晚于入住日期");
        }

        // 验证旅客和房间数量
        if (adults == null || adults < 1) {
            return createErrorResponse("成人数量必须大于等于1");
        }
        if (rooms == null || rooms < 1) {
            return createErrorResponse("房间数量必须大于等于1");
        }

        return null; // 验证通过
    }

    /**
     * 验证活动搜索参数
     */
    public Map<String, Object> validateActivitySearchParams(Map<String, Object> arguments) {
        Double latitude = (Double) arguments.get("latitude");
        Double longitude = (Double) arguments.get("longitude");
        Integer radius = (Integer) arguments.getOrDefault("radius", 50);

        // 验证坐标
        if (!isValidLatitude(latitude)) {
            return createErrorResponse("无效的纬度值: " + latitude + "，纬度必须在-90到90之间");
        }
        if (!isValidLongitude(longitude)) {
            return createErrorResponse("无效的经度值: " + longitude + "，经度必须在-180到180之间");
        }
        if (!isValidRadius(radius)) {
            return createErrorResponse("无效的搜索半径: " + radius + "，半径必须在1到500公里之间");
        }

        return null; // 验证通过
    }

    /**
     * 安全地获取字符串参数
     */
    public String getStringParam(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 安全地获取整数参数
     */
    public Integer getIntegerParam(Map<String, Object> arguments, String key, Integer defaultValue) {
        Object value = arguments.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 安全地获取双精度参数
     */
    public Double getDoubleParam(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 安全地获取布尔参数
     */
    public Boolean getBooleanParam(Map<String, Object> arguments, String key, Boolean defaultValue) {
        Object value = arguments.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
}
