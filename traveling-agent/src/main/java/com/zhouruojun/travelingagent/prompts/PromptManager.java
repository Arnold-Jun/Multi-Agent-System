package com.zhouruojun.travelingagent.prompts;

import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.agent.state.SubgraphState;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 新的提示词管理器
 * 使用依赖注入管理所有提示词提供者
 */
@Slf4j
@Component
public class PromptManager {
    
    private final Map<String, MainGraphPromptProvider> mainGraphProviders = new HashMap<>();
    private final Map<String, SubgraphPromptProvider> subgraphProviders = new HashMap<>();
    
    @Autowired
    public PromptManager(List<MainGraphPromptProvider> mainGraphProviders,
                        List<SubgraphPromptProvider> subgraphProviders) {
        // 注册主图提示词提供者
        for (MainGraphPromptProvider provider : mainGraphProviders) {
            this.mainGraphProviders.put(provider.getAgentName(), provider);
        }
        
        // 注册子图提示词提供者
        for (SubgraphPromptProvider provider : subgraphProviders) {
            this.subgraphProviders.put(provider.getAgentName(), provider);
        }
    }
    
    /**
     * 构建主图智能体消息
     */
    public List<ChatMessage> buildMainGraphMessages(String agentName, MainGraphState state) {
        MainGraphPromptProvider provider = mainGraphProviders.get(agentName);
        if (provider == null) {
            throw new IllegalArgumentException("No prompt provider found for main graph agent: " + agentName);
        }
        
        log.debug("Building messages for main graph agent: {}", agentName);
        return provider.buildMessages(state);
    }
    
    /**
     * 构建子图智能体消息
     */
    public List<ChatMessage> buildSubgraphMessages(String agentName, SubgraphState state) {
        SubgraphPromptProvider provider = subgraphProviders.get(agentName);
        if (provider == null) {
            throw new IllegalArgumentException("No prompt provider found for subgraph agent: " + agentName);
        }
        
        log.debug("Building messages for subgraph agent: {}", agentName);
        return provider.buildMessages(state);
    }
    
    /**
     * 获取主图智能体系统提示词
     */
    public String getMainGraphSystemPrompt(String agentName, String scenario) {
        MainGraphPromptProvider provider = mainGraphProviders.get(agentName);
        if (provider == null) {
            throw new IllegalArgumentException("No prompt provider found for main graph agent: " + agentName);
        }
        
        return provider.getSystemPrompt(scenario);
    }
    
    /**
     * 获取子图智能体系统提示词
     */
    public String getSubgraphSystemPrompt(String agentName) {
        SubgraphPromptProvider provider = subgraphProviders.get(agentName);
        if (provider == null) {
            throw new IllegalArgumentException("No prompt provider found for subgraph agent: " + agentName);
        }
        
        return provider.getSystemPrompt();
    }
    
    /**
     * 检查主图智能体是否支持指定场景
     */
    public boolean supportsMainGraphScenario(String agentName, String scenario) {
        MainGraphPromptProvider provider = mainGraphProviders.get(agentName);
        return provider != null && provider.supportsScenario(scenario);
    }
    
    /**
     * 获取所有注册的主图智能体名称
     */
    public List<String> getMainGraphAgentNames() {
        return mainGraphProviders.keySet().stream().toList();
    }
    
    /**
     * 获取所有注册的子图智能体名称
     */
    public List<String> getSubgraphAgentNames() {
        return subgraphProviders.keySet().stream().toList();
    }
    
    /**
     * 获取主图智能体支持的场景
     */
    public List<String> getMainGraphScenarios(String agentName) {
        MainGraphPromptProvider provider = mainGraphProviders.get(agentName);
        if (provider == null) {
            throw new IllegalArgumentException("No prompt provider found for main graph agent: " + agentName);
        }
        
        return provider.getSupportedScenarios();
    }
}
