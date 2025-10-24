package com.zhouruojun.travelingagent.prompts;

import java.util.List;

/**
 * 提示词提供者接口
 * 定义所有提示词提供者的基本契约
 */
public interface PromptProvider {
    
    /**
     * 获取智能体名称
     */
    String getAgentName();
    
    /**
     * 获取支持的场景列表
     */
    List<String> getSupportedScenarios();
    
    /**
     * 检查是否支持指定场景
     */
    boolean supportsScenario(String scenario);
}
