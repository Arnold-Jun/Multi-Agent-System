package com.zhouruojun.travelingagent.a2a;

import com.zhouruojun.a2acore.spec.AgentCapabilities;
import com.zhouruojun.a2acore.spec.AgentCard;
import com.zhouruojun.a2acore.spec.AgentSkill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 旅游Agent卡片配置
 *
 */
@Configuration
public class TravelingAgentCard {

    @Bean
    public AgentCard createAgentCard() {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setPushNotifications(true);
        capabilities.setStreaming(true);
        capabilities.setStateTransitionHistory(true);
        
        AgentSkill skill = AgentSkill.builder()
                .id("travel_planning")
                .name("旅游规划工具")
                .description("帮助用户完成旅游信息收集、行程规划、预订执行等一系列旅游任务")
                .tags(Arrays.asList("旅游", "travel", "行程", "规划"))
                .examples(Collections.singletonList("请帮我制定一个完整的旅游计划"))
                .inputModes(List.of("text", "data", "file"))
                .outputModes(List.of("text", "data", "file"))
                .build();
                
        AgentCard agentCard = new AgentCard();
        agentCard.setName("traveling-agent");
        agentCard.setDescription("旅游智能体，提供旅游信息收集、行程规划、预订执行等功能");
        agentCard.setUrl("http://localhost:8085");
        agentCard.setVersion("1.0.0");
        agentCard.setCapabilities(capabilities);
        agentCard.setSkills(Collections.singletonList(skill));
        agentCard.setDefaultInputModes(List.of("text", "data", "file"));
        agentCard.setDefaultOutputModes(List.of("text", "data", "file"));
        return agentCard;
    }
}

