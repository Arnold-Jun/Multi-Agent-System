package com.zhouruojun.dataanalysisagent.a2a;

import com.zhouruojun.a2acore.spec.AgentCapabilities;
import com.zhouruojun.a2acore.spec.AgentCard;
import com.zhouruojun.a2acore.spec.AgentSkill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 数据分析Agent卡片配置
 *
 */
@Configuration
public class DataAnalysisAgentCard {

    @Bean
    public AgentCard createAgentCard() {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setPushNotifications(true);
        capabilities.setStreaming(true);
        capabilities.setStateTransitionHistory(true);
        
        AgentSkill skill = AgentSkill.builder()
                .id("data_analysis")
                .name("数据分析工具")
                .description("帮助用户完成数据搜索、统计分析、可视化等一系列数据分析任务")
                .tags(Arrays.asList("数据分析", "data analysis", "统计", "可视化"))
                .examples(Collections.singletonList("请帮我分析数据并生成图表"))
                .inputModes(List.of("text", "data"))
                .outputModes(List.of("text", "data", "file"))
                .build();
                
        AgentCard agentCard = new AgentCard();
        agentCard.setName("data-analysis-agent");
        agentCard.setDescription("数据分析智能体，提供数据搜索、统计分析、可视化等功能");
        agentCard.setUrl("http://localhost:8082");
        agentCard.setVersion("1.0.0");
        agentCard.setCapabilities(capabilities);
        agentCard.setSkills(Collections.singletonList(skill));
        agentCard.setDefaultInputModes(List.of("text", "data"));
        agentCard.setDefaultOutputModes(List.of("text", "data", "file"));
        return agentCard;
    }
}