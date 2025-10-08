package com.zhouruojun.jobsearchagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouruojun.jobsearchagent.config.BossApiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Boss直聘Mock API服务
 * 负责与boss-mock-api项目进行HTTP通信
 */
@Slf4j
@Service
public class BossApiService {
    
    @Autowired
    private BossApiConfig bossApiConfig;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public BossApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 搜索职位（完整参数版本）
     */
    public Map<String, Object> searchJobs(String city, String jobTitle, String salaryRange, 
                                        String jobCategory, String experience, String education, 
                                        String companyScale, String industry, String keywords, 
                                        String sortBy, String sortOrder, int page, int size) {
        try {
            // 构建查询参数
            StringBuilder queryParams = new StringBuilder();
            if (city != null && !city.trim().isEmpty()) {
                queryParams.append("city=").append(encodeParam(city)).append("&");
            }
            if (jobTitle != null && !jobTitle.trim().isEmpty()) {
                queryParams.append("jobTitle=").append(encodeParam(jobTitle)).append("&");
            }
            if (salaryRange != null && !salaryRange.trim().isEmpty()) {
                queryParams.append("salaryRange=").append(encodeParam(salaryRange)).append("&");
            }
            if (jobCategory != null && !jobCategory.trim().isEmpty()) {
                queryParams.append("jobCategory=").append(encodeParam(jobCategory)).append("&");
            }
            if (experience != null && !experience.trim().isEmpty()) {
                queryParams.append("experience=").append(encodeParam(experience)).append("&");
            }
            if (education != null && !education.trim().isEmpty()) {
                queryParams.append("education=").append(encodeParam(education)).append("&");
            }
            if (companyScale != null && !companyScale.trim().isEmpty()) {
                queryParams.append("companyScale=").append(encodeParam(companyScale)).append("&");
            }
            if (industry != null && !industry.trim().isEmpty()) {
                queryParams.append("industry=").append(encodeParam(industry)).append("&");
            }
            if (keywords != null && !keywords.trim().isEmpty()) {
                queryParams.append("keywords=").append(encodeParam(keywords)).append("&");
            }
            if (sortBy != null && !sortBy.trim().isEmpty()) {
                queryParams.append("sortBy=").append(encodeParam(sortBy)).append("&");
            }
            if (sortOrder != null && !sortOrder.trim().isEmpty()) {
                queryParams.append("sortOrder=").append(encodeParam(sortOrder)).append("&");
            }
            queryParams.append("page=").append(page).append("&");
            queryParams.append("size=").append(size);
            
            String url = bossApiConfig.getBaseUrl() + "/simple/jobs/search?" + queryParams.toString();
            
            log.info("调用Boss API搜索职位: {}", url);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMillis(bossApiConfig.getReadTimeout()))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode jsonNode = objectMapper.readTree(response.body());
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) objectMapper.convertValue(jsonNode.get("data"), Map.class);
                return result;
            } else {
                log.error("Boss API搜索职位失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                return createErrorResponse("搜索职位失败，状态码: " + response.statusCode());
            }
            
        } catch (Exception e) {
            log.error("调用Boss API搜索职位异常", e);
            return createErrorResponse("搜索职位异常: " + e.getMessage());
        }
    }

    /**
     * 搜索职位（简化版本，保持向后兼容）
     */
    public Map<String, Object> searchJobs(String city, String jobTitle, String salaryRange, 
                                        String jobCategory, int page, int size) {
        return searchJobs(city, jobTitle, salaryRange, jobCategory, null, null, null, null, null, null, null, page, size);
    }
    
    /**
     * 获取职位详情
     */
    public Map<String, Object> getJobDetails(String jobId) {
        try {
            String url = bossApiConfig.getBaseUrl() + "/simple/jobs/" + jobId;
            
            log.info("调用Boss API获取职位详情: {}", url);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMillis(bossApiConfig.getReadTimeout()))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode jsonNode = objectMapper.readTree(response.body());
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) objectMapper.convertValue(jsonNode.get("data"), Map.class);
                return result;
            } else {
                log.error("Boss API获取职位详情失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                return createErrorResponse("获取职位详情失败，状态码: " + response.statusCode());
            }
            
        } catch (Exception e) {
            log.error("调用Boss API获取职位详情异常", e);
            return createErrorResponse("获取职位详情异常: " + e.getMessage());
        }
    }
    
    /**
     * 获取公司信息
     */
    public Map<String, Object> getCompanyInfo(String companyName) {
        try {
            String url = bossApiConfig.getBaseUrl() + "/simple/companies/info?companyName=" + encodeParam(companyName);
            
            log.info("调用Boss API获取公司信息: {}", url);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMillis(bossApiConfig.getReadTimeout()))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode jsonNode = objectMapper.readTree(response.body());
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) objectMapper.convertValue(jsonNode.get("data"), Map.class);
                return result;
            } else {
                log.error("Boss API获取公司信息失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                return createErrorResponse("获取公司信息失败，状态码: " + response.statusCode());
            }
            
        } catch (Exception e) {
            log.error("调用Boss API获取公司信息异常", e);
            return createErrorResponse("获取公司信息异常: " + e.getMessage());
        }
    }
    
    /**
     * 投递职位（完整参数版本）
     */
    public Map<String, Object> applyForJob(String jobId, String resumeInfo, String coverLetter, String userId) {
        try {
            String url = bossApiConfig.getBaseUrl() + "/simple/jobs/" + jobId + "/apply";
            
            // 构建请求参数
            StringBuilder formData = new StringBuilder();
            formData.append("resumeInfo=").append(encodeParam(resumeInfo != null ? resumeInfo : ""));
            if (coverLetter != null && !coverLetter.trim().isEmpty()) {
                formData.append("&coverLetter=").append(encodeParam(coverLetter));
            }
            if (userId != null && !userId.trim().isEmpty()) {
                formData.append("&userId=").append(encodeParam(userId));
            }
            
            log.info("调用Boss API投递职位: {}", url);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData.toString()))
                    .timeout(Duration.ofMillis(bossApiConfig.getReadTimeout()))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode jsonNode = objectMapper.readTree(response.body());
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) objectMapper.convertValue(jsonNode.get("data"), Map.class);
                return result;
            } else {
                log.error("Boss API投递职位失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                return createErrorResponse("投递职位失败，状态码: " + response.statusCode());
            }
            
        } catch (Exception e) {
            log.error("调用Boss API投递职位异常", e);
            return createErrorResponse("投递职位异常: " + e.getMessage());
        }
    }

    /**
     * 投递职位（简化版本，保持向后兼容）
     */
    public Map<String, Object> applyForJob(String jobId, String resumeInfo, String coverLetter) {
        return applyForJob(jobId, resumeInfo, coverLetter, null);
    }
    
    /**
     * 检查Boss API服务是否可用
     */
    public boolean isServiceAvailable() {
        try {
            String url = bossApiConfig.getBaseUrl() + "/simple/jobs/search?page=0&size=1";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMillis(5000))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
            
        } catch (Exception e) {
            log.warn("Boss API服务不可用: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取Boss API服务状态
     */
    public Map<String, Object> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("baseUrl", bossApiConfig.getBaseUrl());
        status.put("enabled", bossApiConfig.isEnabled());
        status.put("available", isServiceAvailable());
        status.put("connectTimeout", bossApiConfig.getConnectTimeout());
        status.put("readTimeout", bossApiConfig.getReadTimeout());
        return status;
    }
    
    /**
     * URL编码参数
     */
    private String encodeParam(String param) {
        try {
            return java.net.URLEncoder.encode(param, "UTF-8");
        } catch (Exception e) {
            return param;
        }
    }
    
    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String errorMessage) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", true);
        errorResponse.put("message", errorMessage);
        errorResponse.put("timestamp", System.currentTimeMillis());
        return errorResponse;
    }
}
