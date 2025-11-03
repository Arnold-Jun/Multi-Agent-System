package com.zhouruojun.travelingagent.prompts;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 提示词提供者抽象基类
 * 提供通用的提示词管理功能
 */
@Slf4j
public abstract class BasePromptProvider implements PromptProvider {
    
    protected final String agentName;
    protected final List<String> supportedScenarios;
    
    protected BasePromptProvider(String agentName, List<String> supportedScenarios) {
        this.agentName = agentName;
        this.supportedScenarios = supportedScenarios;
    }
    
    @Override
    public String getAgentName() {
        return agentName;
    }
    
    @Override
    public List<String> getSupportedScenarios() {
        return supportedScenarios;
    }
    
    @Override
    public boolean supportsScenario(String scenario) {
        return supportedScenarios.contains(scenario);
    }
    
    /**
     * 验证场景是否支持
     */
    protected void validateScenario(String scenario) {
        if (!supportsScenario(scenario)) {
            throw new IllegalArgumentException(
                String.format("Agent %s does not support scenario: %s. Supported scenarios: %s", 
                    agentName, scenario, supportedScenarios)
            );
        }
    }
    
    /**
     * 记录提示词构建日志
     */
    protected void logPromptBuilding(String scenario, String details) {
        log.debug("Building prompt for agent: {}, scenario: {}, details: {}", 
            agentName, scenario, details);
    }
    
    /**
     * 获取当前时间信息字符串
     * 格式：2024年1月15日 星期一 14:30:25
     */
    protected String getCurrentTimeInfo() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm:ss");
        return now.format(formatter);
    }
    
    
    /**
     * 在系统提示词中添加时间信息
     * 子类可以重写此方法来自定义时间信息的添加方式
     */
    protected String addTimeInfoToSystemPrompt(String originalPrompt) {
        String timeInfo = getCurrentTimeInfo();
        return String.format("""
            **当前时间信息**：
            - 当前时间：%s
            - 时区：美东时间
            
            %s
            """, timeInfo, originalPrompt);
    }
}
