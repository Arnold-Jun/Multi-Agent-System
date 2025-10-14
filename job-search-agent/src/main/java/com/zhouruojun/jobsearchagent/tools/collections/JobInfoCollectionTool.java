package com.zhouruojun.jobsearchagent.tools.collections;

import com.zhouruojun.jobsearchagent.agent.state.BaseAgentState;
import com.zhouruojun.jobsearchagent.service.BossApiService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 岗位信息收集工具集合
 * 专门为岗位信息收集子智能体提供工具方法
 * 职责：岗位搜索、详细信息获取、岗位投递
 */
@Slf4j
@Component
public class JobInfoCollectionTool {
    
    @Autowired
    private BossApiService bossApiService;

    /**
     * 搜索岗位信息
     * 支持两种调用方式：
     * 1. 标准参数调用：所有参数都是可选的，null值表示不进行过滤
     * 2. 灵活字符串调用：将 searchCriteria 设置为搜索条件字符串，其他参数将被忽略
     */
    @Tool("""
        搜索职位信息，支持多种筛选条件，所有参数都是可选的，null值表示不进行过滤
        
        参数说明：
        - 城市：支持精确匹配和区域匹配，如"上海"、"北京"、"长三角"、"珠三角"、"京津冀"等
        - 岗位名称：支持多关键词匹配（空格分隔），如"AI算法工程师 机器学习研发工程师"、"Java开发 后端工程师"等
        - 薪资范围：支持"15k-30k"、"20k-40k"、"面议"、"不限"等格式
        - 岗位类别：支持多值和同义词匹配，如"社招 校招"、"校园招聘 应届生招聘"等
        - 经验要求：支持多值和同义词匹配，如"应届毕业生 1-3年"、"应届生 中级"等
        - 学历要求：支持多值匹配，如"本科 硕士"、"硕士 博士"等
        - 公司规模：如"20-99人"、"100-499人"、"500-999人"、"1000-9999人"、"10000人以上"
        - 行业：如"互联网"、"人工智能"、"金融科技"、"企业服务"、"电商"等
        - 关键词：空格分隔的多个关键词，支持部分匹配
        - 排序方式：time(时间)、salary(薪资)、popularity(热度)
        - 排序顺序：asc(升序)、desc(降序)
        
        返回详细信息包括职责描述、岗位要求、公司信息等
        """)
    public String searchJobs(String city, String jobTitle, String salaryRange, String jobCategory, 
                           String experience, String education, String companyScale, String industry, 
                           String keywords, String sortBy, String sortOrder, BaseAgentState state) {
        
        // 参数标准化处理
        city = normalizeParameter(city);
        jobTitle = normalizeParameter(jobTitle);
        salaryRange = normalizeParameter(salaryRange);
        jobCategory = normalizeParameter(jobCategory);
        experience = normalizeParameter(experience);
        education = normalizeParameter(education);
        companyScale = normalizeParameter(companyScale);
        industry = normalizeParameter(industry);
        keywords = normalizeParameter(keywords);
        sortBy = normalizeSortBy(sortBy);
        sortOrder = normalizeSortOrder(sortOrder);
        
        log.info("搜索职位参数: 城市={}, 岗位={}, 薪资={}, 类别={}, 经验={}, 学历={}, 公司规模={}, 行业={}, 关键词={}, 排序={}", 
                city, jobTitle, salaryRange, jobCategory, experience, education, companyScale, industry, keywords, sortBy);
        
        try {
            // 调用Boss API搜索职位
            Map<String, Object> searchResult = bossApiService.searchJobs(city, jobTitle, salaryRange, jobCategory, 
                    experience, education, companyScale, industry, keywords, sortBy, sortOrder, 0, 20);
            
            // 检查是否有错误
            if (searchResult.containsKey("error") && (Boolean) searchResult.get("error")) {
                log.error("Boss API搜索职位失败: {}", searchResult.get("message"));
                return "搜索职位失败: " + searchResult.get("message") + 
                       "\n\n请检查网络连接或稍后重试。";
            }
            
            // 解析搜索结果
            return formatSearchResult(searchResult, city, jobTitle, salaryRange, jobCategory);
            
        } catch (Exception e) {
            log.error("搜索职位异常", e);
            return "搜索职位异常: " + e.getMessage() + 
                   "\n\n请检查网络连接或稍后重试。";
        }
    }

    /**
     * 投递职位
     * 向指定职位投递简历
     */
    @Tool("向指定职位投递简历，支持附加求职信。需要提供有效的职位ID、简历信息和可选的求职信内容")
    public String applyForJob(String jobId, String resumeInfo, String coverLetter, BaseAgentState state) {
        log.info("投递岗位: 岗位ID={}, 简历信息长度={}, 求职信长度={}", 
                jobId, 
                resumeInfo != null ? resumeInfo.length() : 0, 
                coverLetter != null ? coverLetter.length() : 0);
        
        try {
            // 调用Boss API投递职位
            Map<String, Object> applicationResult = bossApiService.applyForJob(jobId, resumeInfo, coverLetter, null);
            
            // 检查是否有错误
            if (applicationResult.containsKey("error") && (Boolean) applicationResult.get("error")) {
                log.error("Boss API投递职位失败: {}", applicationResult.get("message"));
                return "投递职位失败: " + applicationResult.get("message") + 
                       "\n\n请检查网络连接或稍后重试。";
            }
            
            // 格式化投递结果
            return formatApplicationResult(applicationResult, jobId, resumeInfo, coverLetter);
            
        } catch (Exception e) {
            log.error("投递职位异常", e);
            return "投递职位异常: " + e.getMessage() + 
                   "\n\n请检查网络连接或稍后重试。";
        }
    }


    /**
     * 获取职位详情
     * 根据职位ID获取详细的职位信息
     */
    @Tool("根据职位ID获取详细的职位信息，包括职责描述、岗位要求、公司信息等完整内容")
    public String getJobDetails(String jobId, BaseAgentState state) {
        log.info("获取职位详情: 职位ID={}", jobId);
        
        try {
            // 调用Boss API获取职位详情
            Map<String, Object> jobDetails = bossApiService.getJobDetails(jobId);
            
            // 检查是否有错误
            if (jobDetails.containsKey("error") && (Boolean) jobDetails.get("error")) {
                log.error("Boss API获取职位详情失败: {}", jobDetails.get("message"));
                return "获取职位详情失败: " + jobDetails.get("message") + 
                       "\n\n请检查职位ID是否正确或稍后重试。";
            }
            
            // 格式化职位详情
            return formatJobDetails(jobDetails, jobId);
            
        } catch (Exception e) {
            log.error("获取职位详情异常", e);
            return "获取职位详情异常: " + e.getMessage() + 
                   "\n\n请检查网络连接或稍后重试。";
        }
    }

    /**
     * 获取公司信息
     * 根据公司名称获取招聘公司的详细信息
     */
    @Tool("根据公司名称获取招聘公司的详细信息，包括公司规模、行业、融资情况、福利待遇、业务范围、核心产品等")
    public String getCompanyInfo(String companyName, BaseAgentState state) {
        log.info("获取公司信息: 公司名称={}", companyName);
        
        try {
            // 调用Boss API获取公司信息
            Map<String, Object> companyInfo = bossApiService.getCompanyInfo(companyName);
            
            if (companyInfo.containsKey("error") && (Boolean) companyInfo.get("error")) {
                log.error("Boss API获取公司信息失败: {}", companyInfo.get("message"));
                return "获取公司信息失败: " + companyInfo.get("message") + 
                       "\n\n请检查公司名称是否正确或稍后重试。";
            }
            
            return formatCompanyInfo(companyInfo, companyName);
            
        } catch (Exception e) {
            log.error("获取公司信息异常", e);
            return "获取公司信息异常: " + e.getMessage() + 
                   "\n\n请检查网络连接或稍后重试。";
        }
    }
    
    /**
     * 格式化搜索结果
     */
    private String formatSearchResult(Map<String, Object> searchResult, String city, String jobTitle, 
                                    String salaryRange, String jobCategory) {
        StringBuilder result = new StringBuilder();
        
        result.append("Boss直聘岗位搜索完成\n\n");
        result.append("**搜索条件**:\n");
        result.append("- 城市: ").append(city != null ? city : "全国").append("\n");
        result.append("- 岗位名称: ").append(jobTitle != null ? jobTitle : "全部岗位").append("\n");
        result.append("- 薪资范围: ").append(salaryRange != null ? salaryRange : "不限").append("\n");
        result.append("- 岗位类别: ").append(jobCategory != null ? jobCategory : "全部类别").append("\n\n");
        
        // 获取职位列表
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> jobs = (List<Map<String, Object>>) searchResult.get("jobs");
        Integer total = (Integer) searchResult.get("total");
        
        if (jobs != null && !jobs.isEmpty()) {
            result.append("**搜索结果** (共找到").append(total).append("个岗位):\n\n");
            
            for (int i = 0; i < Math.min(jobs.size(), 10); i++) {
                Map<String, Object> job = jobs.get(i);
                result.append("**").append(i + 1).append(". ").append(job.get("title")).append("** - 岗位ID: ").append(job.get("jobId")).append("\n");
                result.append("- 公司: ").append(job.get("company")).append("\n");
                result.append("- 类别: ").append(job.get("category")).append("\n");
                result.append("- 薪资: ").append(job.get("salary")).append("\n");
                result.append("- 地点: ").append(job.get("city")).append("\n");
                result.append("- 经验: ").append(job.get("experience")).append("\n");
                result.append("- 学历: ").append(job.get("education")).append("\n");
                result.append("- 发布时间: ").append(job.get("publishTime")).append("\n");
                
                // 添加职责描述
                @SuppressWarnings("unchecked")
                List<String> responsibilities = (List<String>) job.get("responsibilities");
                if (responsibilities != null && !responsibilities.isEmpty()) {
                    result.append("\n**职责描述**:\n");
                    for (int j = 0; j < Math.min(responsibilities.size(), 5); j++) {
                        result.append(j + 1).append(". ").append(responsibilities.get(j)).append("\n");
                    }
                }
                
                // 添加岗位要求
                @SuppressWarnings("unchecked")
                List<String> requirements = (List<String>) job.get("requirements");
                if (requirements != null && !requirements.isEmpty()) {
                    result.append("\n**岗位要求**:\n");
                    for (int j = 0; j < Math.min(requirements.size(), 5); j++) {
                        result.append(j + 1).append(". ").append(requirements.get(j)).append("\n");
                    }
                }
                
                result.append("\n---\n\n");
            }
            
            // 添加搜索统计
            result.append("**搜索统计**:\n");
            result.append("- 总职位数: ").append(total).append("个\n");
            result.append("- 显示职位: ").append(Math.min(jobs.size(), 10)).append("个\n");
            result.append("- 搜索时间: ").append(searchResult.get("searchTime")).append("\n");
            
        } else {
            result.append("**搜索结果**: 未找到符合条件的职位\n");
        }
        
        return result.toString();
    }
    
    /**
     * 格式化公司信息
     */
    private String formatCompanyInfo(Map<String, Object> companyInfo, String companyName) {
        StringBuilder result = new StringBuilder();
        
        result.append("公司信息获取完成\n\n");
        result.append("**公司基本信息**:\n");
        result.append("- 公司名称: ").append(companyName).append("\n");
        
        if (companyInfo != null && !companyInfo.isEmpty()) {
            result.append("- 公司规模: ").append(companyInfo.get("scale")).append("\n");
            result.append("- 所属行业: ").append(companyInfo.get("industry")).append("\n");
            result.append("- 融资阶段: ").append(companyInfo.get("stage")).append("\n");
            result.append("- 成立时间: ").append(companyInfo.get("foundedYear")).append("\n");
            result.append("- 公司地址: ").append(companyInfo.get("location")).append("\n");
            result.append("- 公司性质: ").append(companyInfo.get("companyType")).append("\n");
            
            // 添加福利待遇
            @SuppressWarnings("unchecked")
            List<String> benefits = (List<String>) companyInfo.get("benefits");
            if (benefits != null && !benefits.isEmpty()) {
                result.append("\n**福利待遇**:\n");
                for (String benefit : benefits) {
                    result.append("- ").append(benefit).append("\n");
                }
            }
            
            // 添加公司简介
            String description = (String) companyInfo.get("description");
            if (description != null && !description.isEmpty()) {
                result.append("\n**公司简介**:\n");
                result.append(description).append("\n");
            }
            
            // 添加业务范围
            @SuppressWarnings("unchecked")
            List<String> businessScope = (List<String>) companyInfo.get("businessScope");
            if (businessScope != null && !businessScope.isEmpty()) {
                result.append("\n**业务范围**:\n");
                for (String scope : businessScope) {
                    result.append("- ").append(scope).append("\n");
                }
            }
        } else {
            result.append("- 公司规模: 信息暂未提供\n");
            result.append("- 所属行业: 信息暂未提供\n");
            result.append("- 融资阶段: 信息暂未提供\n");
        }
        
        return result.toString();
    }
    
    
    /**
     * 格式化职位详情
     */
    private String formatJobDetails(Map<String, Object> jobDetails, String jobId) {
        StringBuilder result = new StringBuilder();
        
        result.append("职位详情获取完成\n\n");
        result.append("**职位基本信息**:\n");
        result.append("- 职位ID: ").append(jobId).append("\n");
        result.append("- 职位名称: ").append(jobDetails.get("title")).append("\n");
        result.append("- 公司名称: ").append(jobDetails.get("company")).append("\n");
        result.append("- 工作地点: ").append(jobDetails.get("city")).append("\n");
        result.append("- 职位类别: ").append(jobDetails.get("category")).append("\n");
        result.append("- 薪资范围: ").append(jobDetails.get("salary")).append("\n");
        result.append("- 经验要求: ").append(jobDetails.get("experience")).append("\n");
        result.append("- 学历要求: ").append(jobDetails.get("education")).append("\n");
        result.append("- 发布时间: ").append(jobDetails.get("publishTime")).append("\n\n");
        
        // 添加职责描述
        @SuppressWarnings("unchecked")
        List<String> responsibilities = (List<String>) jobDetails.get("responsibilities");
        if (responsibilities != null && !responsibilities.isEmpty()) {
            result.append("**职责描述**:\n");
            for (int i = 0; i < responsibilities.size(); i++) {
                result.append(i + 1).append(". ").append(responsibilities.get(i)).append("\n");
            }
            result.append("\n");
        }
        
        // 添加岗位要求
        @SuppressWarnings("unchecked")
        List<String> requirements = (List<String>) jobDetails.get("requirements");
        if (requirements != null && !requirements.isEmpty()) {
            result.append("**岗位要求**:\n");
            for (int i = 0; i < requirements.size(); i++) {
                result.append(i + 1).append(". ").append(requirements.get(i)).append("\n");
            }
            result.append("\n");
        }
        
        // 添加公司信息
        @SuppressWarnings("unchecked")
        Map<String, Object> companyInfo = (Map<String, Object>) jobDetails.get("companyInfo");
        if (companyInfo != null) {
            result.append("**公司信息**:\n");
            result.append("- 公司名称: ").append(companyInfo.get("name")).append("\n");
            result.append("- 公司规模: ").append(companyInfo.get("scale")).append("\n");
            result.append("- 所属行业: ").append(companyInfo.get("industry")).append("\n");
            result.append("- 融资阶段: ").append(companyInfo.get("financing")).append("\n");
            result.append("- 公司地址: ").append(companyInfo.get("location")).append("\n");
        }
        
        return result.toString();
    }
    
    

    /**
     * 格式化投递结果
     */
    private String formatApplicationResult(Map<String, Object> applicationResult, String jobId, 
                                         String resumeInfo, String coverLetter) {
        StringBuilder result = new StringBuilder();
        
        result.append("Boss直聘岗位投递完成\n\n");
        result.append("**投递信息**:\n");
        result.append("- 岗位ID: ").append(jobId).append("\n");
        result.append("- 投递时间: ").append(applicationResult.get("applyTime")).append("\n");
        result.append("- 投递状态: ").append(applicationResult.get("status")).append("\n");
        result.append("- 投递编号: ").append(applicationResult.get("applicationId")).append("\n\n");
        
        // 添加职位信息
        @SuppressWarnings("unchecked")
        Map<String, Object> jobInfo = (Map<String, Object>) applicationResult.get("jobInfo");
        if (jobInfo != null) {
            result.append("**职位信息**:\n");
            result.append("- 职位名称: ").append(jobInfo.get("title")).append("\n");
            result.append("- 公司名称: ").append(jobInfo.get("company")).append("\n");
            result.append("- 工作地点: ").append(jobInfo.get("city")).append("\n");
            result.append("- 薪资范围: ").append(jobInfo.get("salary")).append("\n\n");
        }
        
        // 添加投递内容摘要
        @SuppressWarnings("unchecked")
        Map<String, Object> applicationSummary = (Map<String, Object>) applicationResult.get("applicationSummary");
        if (applicationSummary != null) {
            result.append("**投递内容**:\n");
            result.append("- 简历信息: 已提交 (").append(applicationSummary.get("resumeLength")).append("字符)\n");
            result.append("- 求职信: ").append(Boolean.TRUE.equals(applicationSummary.get("hasCoverLetter")) ? 
                    "已提交 (" + applicationSummary.get("coverLetterLength") + "字符)" : "未提交").append("\n\n");
        }
        
        // 添加处理进度
        result.append("**处理进度**:\n");
        result.append("- 当前状态: ").append(applicationResult.get("currentStatus")).append("\n");
        result.append("- 下一步: ").append(applicationResult.get("nextStatus")).append("\n");
        result.append("- 预计查看时间: ").append(applicationResult.get("estimatedViewTime")).append("\n");
        result.append("- 预计回复时间: ").append(applicationResult.get("estimatedResponseTime")).append("\n\n");
        
        // 添加投递统计
        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) applicationResult.get("statistics");
        if (statistics != null) {
            result.append("**投递统计**:\n");
            result.append("- 总投递数: ").append(statistics.get("totalApplications")).append("\n");
            result.append("- 成功率: ").append(statistics.get("successRate")).append("\n");
            result.append("- 平均响应时间: ").append(statistics.get("averageResponseTime")).append("\n");
            result.append("- 面试率: ").append(statistics.get("interviewRate")).append("\n");
            result.append("- 录用率: ").append(statistics.get("offerRate")).append("\n\n");
        }
        
        // 添加建议
        @SuppressWarnings("unchecked")
        List<String> suggestions = (List<String>) applicationResult.get("suggestions");
        if (suggestions != null && !suggestions.isEmpty()) {
            result.append("**投递建议**:\n");
            for (String suggestion : suggestions) {
                result.append("- ").append(suggestion).append("\n");
            }
            result.append("\n");
        }
        
        // 添加后续跟进计划
        @SuppressWarnings("unchecked")
        Map<String, Object> followUp = (Map<String, Object>) applicationResult.get("followUp");
        if (followUp != null) {
            result.append("**后续跟进**:\n");
            result.append("- 下一步行动: ").append(followUp.get("nextAction")).append("\n");
            result.append("- 预计时间: ").append(followUp.get("actionTime")).append("\n");
            result.append("- 提醒时间: ").append(followUp.get("reminderTime")).append("\n");
            result.append("- 可撤回: ").append(Boolean.TRUE.equals(followUp.get("canWithdraw")) ? "是" : "否").append("\n");
            if (Boolean.TRUE.equals(followUp.get("canWithdraw"))) {
                result.append("- 撤回截止时间: ").append(followUp.get("withdrawDeadline")).append("\n");
            }
            result.append("\n");
        }
        
        // 添加投递成功消息
        String message = (String) applicationResult.get("message");
        if (message != null && !message.isEmpty()) {
            result.append("**投递反馈**:\n");
            result.append(message).append("\n");
        }
        
        return result.toString();
    }





    /**
     * 标准化参数值
     */
    private String normalizeParameter(String param) {
        if (param == null || param.trim().isEmpty()) {
            return null; // null值表示不进行过滤
        }
        
        String trimmed = param.trim();
        
        // 处理特殊值
        if (trimmed.equals("不限") || trimmed.equals("无要求") || trimmed.equals("任意")) {
            return null; // 不限表示不进行过滤
        }
        
        return trimmed;
    }

    /**
     * 标准化排序方式
     */
    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return "time"; // 默认按时间排序
        }
        
        String trimmed = sortBy.trim().toLowerCase();
        
        switch (trimmed) {
            case "时间":
            case "time":
            case "发布时间":
                return "time";
            case "薪资":
            case "salary":
            case "工资":
                return "salary";
            case "热度":
            case "popularity":
            case "热门":
                return "popularity";
            default:
                return "time"; // 默认值
        }
    }

    /**
     * 标准化排序顺序
     */
    private String normalizeSortOrder(String sortOrder) {
        if (sortOrder == null || sortOrder.trim().isEmpty()) {
            return "desc"; // 默认降序
        }
        
        String trimmed = sortOrder.trim().toLowerCase();
        
        switch (trimmed) {
            case "升序":
            case "asc":
            case "ascending":
                return "asc";
            case "降序":
            case "desc":
            case "descending":
                return "desc";
            default:
                return "desc"; // 默认值
        }
    }

}
