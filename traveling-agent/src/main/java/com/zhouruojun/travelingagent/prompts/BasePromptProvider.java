package com.zhouruojun.travelingagent.prompts;

import lombok.extern.slf4j.Slf4j;

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
}
