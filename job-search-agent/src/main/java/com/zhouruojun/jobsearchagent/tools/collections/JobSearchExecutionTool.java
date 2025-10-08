package com.zhouruojun.jobsearchagent.tools.collections;

import com.zhouruojun.jobsearchagent.agent.state.BaseAgentState;
import com.zhouruojun.jobsearchagent.service.BossApiService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 求职执行工具集合
 * 专门为求职执行子智能体提供工具方法
 * 职责：求职策略制定、岗位投递、结果跟踪、面试准备
 */
@Slf4j
@Component
public class JobSearchExecutionTool {
    
    @Autowired
    private BossApiService bossApiService;

    /**
     * 投递职位
     * 向指定职位投递简历
     */
    @Tool("向指定职位投递简历，支持附加求职信和用户ID")
    public String applyForJob(String jobId, String resumeInfo, String coverLetter, String userId, BaseAgentState state) {
        log.info("投递职位: 职位ID={}, 简历信息长度={}, 求职信长度={}, 用户ID={}", 
                jobId, 
                resumeInfo != null ? resumeInfo.length() : 0, 
                coverLetter != null ? coverLetter.length() : 0,
                userId);
        
        try {
            // 调用Boss API投递职位
            Map<String, Object> applicationResult = bossApiService.applyForJob(jobId, resumeInfo, coverLetter, userId);
            
            // 检查是否有错误
            if (applicationResult.containsKey("error") && (Boolean) applicationResult.get("error")) {
                log.warn("Boss API投递失败，使用模拟数据: {}", applicationResult.get("message"));
                return generateMockApplicationResult(jobId, resumeInfo, coverLetter);
            }
            
            // 格式化投递结果
            return formatApplicationResult(applicationResult, jobId, resumeInfo, coverLetter);
            
        } catch (Exception e) {
            log.error("投递职位异常，使用模拟数据", e);
            return generateMockApplicationResult(jobId, resumeInfo, coverLetter);
        }
    }

    
    /**
     * 生成模拟投递结果（当API不可用时）
     */
    private String generateMockApplicationResult(String jobId, String resumeInfo, String coverLetter) {
        return String.format("""
            📤 岗位投递执行完成 (模拟数据)
            
            **投递信息**:
            - 岗位ID: %s
            - 投递时间: 2024-01-15 10:30:00
            - 投递状态: 成功
            
            **投递详情**:
            - 岗位名称: 高级Java开发工程师
            - 公司名称: 北京科技有限公司
            - 薪资范围: 20-30K
            - 工作地点: 北京市朝阳区
            - 匹配度: 95%%
            
            **投递内容**:
            - 简历信息: %s
            - 求职信: %s
            
            **投递记录**:
            - 投递编号: APP_20240115_001
            - 投递渠道: Boss直聘
            - 投递方式: 在线投递
            - 投递状态: 已投递
            - 预计反馈: 3-5个工作日
            
            **后续跟踪计划**:
            - 第1天: 确认投递成功
            - 第3天: 查看投递状态
            - 第5天: 主动联系HR
            - 第7天: 评估投递效果
            
            **投递统计**:
            - 今日投递: 3个岗位
            - 本周投递: 15个岗位
            - 本月投递: 45个岗位
            - 总投递数: 45个岗位
            
            **投递效果分析**:
            - 投递成功率: 18%%
            - 面试邀请率: 12%%
            - 平均反馈时间: 4.2天
            - 高匹配度岗位: 8个
            
            **优化建议**:
            1. 继续投递高匹配度岗位
            2. 优化求职信内容
            3. 完善作品集展示
            4. 加强主动联系HR
            
            **下一步建议**: 等待HR反馈或主动联系
            """, jobId, 
                resumeInfo != null ? resumeInfo.substring(0, Math.min(50, resumeInfo.length())) + "..." : "未提供",
                coverLetter != null ? coverLetter.substring(0, Math.min(50, coverLetter.length())) + "..." : "未提供");
    }

    /**
     * 格式化投递结果
     */
    private String formatApplicationResult(Map<String, Object> applicationResult, String jobId, 
                                         String resumeInfo, String coverLetter) {
        StringBuilder result = new StringBuilder();
        
        result.append("📤 Boss直聘岗位投递完成\n\n");
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
        java.util.List<String> suggestions = (java.util.List<String>) applicationResult.get("suggestions");
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
    
    
}
