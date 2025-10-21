package com.zhouruojun.travelingagent.config;

import com.zhouruojun.a2acore.core.PushNotificationSenderAuth;
import com.zhouruojun.a2acore.spec.AgentCard;
import com.zhouruojun.a2acore.spec.AgentCapabilities;
import com.zhouruojun.a2acore.spec.AgentProvider;
import com.zhouruojun.a2acore.spec.AgentSkill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * A2A核心组件配置
 * 提供A2A协议所需的Bean
 */
@Configuration
public class A2ACoreConfig {

    /**
     * 推送通知发送认证Bean
     */
    @Bean
    public PushNotificationSenderAuth pushNotificationSenderAuth() {
        return new PushNotificationSenderAuth();
    }

    /**
     * 旅游Agent卡片配置
     */
    @Bean
    public AgentCard agentCard() {
        AgentProvider provider = new AgentProvider("旅游团队", "http://localhost:8083");
        
        AgentCapabilities capabilities = new AgentCapabilities(true, true, true);
        
        List<AgentSkill> skills = Arrays.asList(
                AgentSkill.builder()
                        .id("travel-info-collection")
                        .name("旅游信息收集")
                        .description("收集用户旅游需求、偏好、预算等信息")
                        .tags(Arrays.asList("travel", "info", "collection", "preferences"))
                        .inputModes(Arrays.asList("text", "data"))
                        .outputModes(Arrays.asList("text", "data"))
                        .build(),
                AgentSkill.builder()
                        .id("travel-plan-optimization")
                        .name("旅游计划优化")
                        .description("基于用户需求优化旅游计划，包括路线、时间、预算等")
                        .tags(Arrays.asList("travel", "plan", "optimization", "route"))
                        .inputModes(Arrays.asList("text", "data"))
                        .outputModes(Arrays.asList("text", "data"))
                        .build(),
                AgentSkill.builder()
                        .id("travel-booking-execution")
                        .name("旅游预订执行")
                        .description("执行旅游预订，包括酒店、机票、景点门票等")
                        .tags(Arrays.asList("travel", "booking", "execution", "reservation"))
                        .inputModes(Arrays.asList("text", "data"))
                        .outputModes(Arrays.asList("text", "data"))
                        .build(),
                AgentSkill.builder()
                        .id("travel-advice")
                        .name("旅游建议")
                        .description("提供旅游目的地建议、注意事项、最佳实践等")
                        .tags(Arrays.asList("travel", "advice", "guidance", "recommendations"))
                        .inputModes(Arrays.asList("text", "data"))
                        .outputModes(Arrays.asList("text", "data"))
                        .build()
        );

        return AgentCard.builder()
                .name("traveling-agent")
                .description("专业的旅游规划智能体，支持旅游信息收集、计划优化、预订执行等功能")
                .url("http://localhost:8083")
                .provider(provider)
                .version("1.0.0")
                .capabilities(capabilities)
                .defaultInputModes(Arrays.asList("text", "data"))
                .defaultOutputModes(Arrays.asList("text", "data"))
                .skills(skills)
                .build();
    }
}




