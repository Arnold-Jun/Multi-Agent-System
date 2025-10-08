package com.zhouruojun.jobsearchagent.a2a;

import com.zhouruojun.a2acore.spec.AgentCapabilities;
import com.zhouruojun.a2acore.spec.AgentCard;
import com.zhouruojun.a2acore.spec.AgentSkill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 求职Agent卡片配置
 *
 */
@Configuration
public class JobSearchAgentCard {

    @Bean
    public AgentCard createAgentCard() {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setPushNotifications(true);
        capabilities.setStreaming(true);
        capabilities.setStateTransitionHistory(true);
        
        AgentSkill skill = AgentSkill.builder()
                .id("job_search")
                .name("求职工具")
                .description("帮助用户完成简历分析、岗位搜索、匹配度评估等一系列求职任务")
                .tags(Arrays.asList("求职", "job search", "简历", "招聘"))
                .examples(Collections.singletonList("请帮我分析简历并搜索匹配的岗位"))
                .inputModes(List.of("text", "data", "file"))
                .outputModes(List.of("text", "data", "file"))
                .build();
                
        AgentCard agentCard = new AgentCard();
        agentCard.setName("job-search-agent");
        agentCard.setDescription("求职智能体，提供简历分析、岗位搜索、匹配度评估等功能");
        agentCard.setUrl("http://localhost:8083");
        agentCard.setVersion("1.0.0");
        agentCard.setCapabilities(capabilities);
        agentCard.setSkills(Collections.singletonList(skill));
        agentCard.setDefaultInputModes(List.of("text", "data", "file"));
        agentCard.setDefaultOutputModes(List.of("text", "data", "file"));
        return agentCard;
    }
}
