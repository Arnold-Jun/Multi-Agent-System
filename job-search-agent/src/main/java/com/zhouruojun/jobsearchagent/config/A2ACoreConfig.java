package com.zhouruojun.jobsearchagent.config;

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
     * 求职Agent卡片配置
     */
    @Bean
    public AgentCard agentCard() {
        AgentProvider provider = new AgentProvider("求职团队", "http://localhost:8083");
        
        AgentCapabilities capabilities = new AgentCapabilities(true, true, true);
        
        List<AgentSkill> skills = Arrays.asList(
                AgentSkill.builder()
                        .id("resume-analysis")
                        .name("简历分析")
                        .description("解析简历文件，提取个人信息、工作经历、技能等关键信息")
                        .tags(Arrays.asList("resume", "analysis", "parsing", "extraction"))
                        .inputModes(Arrays.asList("file", "text"))
                        .outputModes(Arrays.asList("text", "data"))
                        .build(),
                AgentSkill.builder()
                        .id("job-search")
                        .name("岗位搜索")
                        .description("搜索匹配的招聘岗位，支持多平台搜索")
                        .tags(Arrays.asList("job", "search", "recruitment", "hiring"))
                        .inputModes(Arrays.asList("text", "data"))
                        .outputModes(Arrays.asList("text", "data"))
                        .build(),
                AgentSkill.builder()
                        .id("matching-analysis")
                        .name("匹配度分析")
                        .description("分析简历与岗位的匹配度，提供匹配度评分")
                        .tags(Arrays.asList("matching", "analysis", "score", "evaluation"))
                        .inputModes(Arrays.asList("data", "text"))
                        .outputModes(Arrays.asList("text", "data"))
                        .build(),
                AgentSkill.builder()
                        .id("career-advice")
                        .name("职业建议")
                        .description("基于分析结果提供职业发展建议和求职指导")
                        .tags(Arrays.asList("career", "advice", "guidance", "development"))
                        .inputModes(Arrays.asList("data", "text"))
                        .outputModes(Arrays.asList("text", "data"))
                        .build()
        );

        return AgentCard.builder()
                .name("job-search-agent")
                .description("专业的求职智能体，支持简历分析、岗位搜索、匹配度评估等功能")
                .url("http://localhost:8083")
                .provider(provider)
                .version("1.0.0")
                .capabilities(capabilities)
                .defaultInputModes(Arrays.asList("text", "file", "data"))
                .defaultOutputModes(Arrays.asList("text", "data", "file"))
                .skills(skills)
                .build();
    }
}
