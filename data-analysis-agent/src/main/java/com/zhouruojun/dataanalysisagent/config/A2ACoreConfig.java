package com.zhouruojun.dataanalysisagent.config;

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
     * 数据分析Agent卡片配置
     */
    @Bean
    public AgentCard agentCard() {
        AgentProvider provider = new AgentProvider("数据分析团队", "http://localhost:8082");
        
        AgentCapabilities capabilities = new AgentCapabilities(true, true, true);
        
        List<AgentSkill> skills = Arrays.asList(
                AgentSkill.builder()
                        .id("data-loading")
                        .name("数据加载")
                        .description("支持CSV、Excel等格式的数据文件加载")
                        .tags(Arrays.asList("data", "loading", "csv", "excel"))
                        .inputModes(Arrays.asList("file", "text"))
                        .outputModes(Arrays.asList("text", "data"))
                        .build(),
                AgentSkill.builder()
                        .id("statistical-analysis")
                        .name("统计分析")
                        .description("提供描述性统计、相关性分析等统计功能")
                        .tags(Arrays.asList("statistics", "analysis", "math"))
                        .inputModes(Arrays.asList("data", "text"))
                        .outputModes(Arrays.asList("text", "data"))
                        .build(),
                AgentSkill.builder()
                        .id("data-visualization")
                        .name("数据可视化")
                        .description("生成各种类型的图表，包括柱状图、折线图、饼图等")
                        .tags(Arrays.asList("visualization", "chart", "graph"))
                        .inputModes(Arrays.asList("data", "text"))
                        .outputModes(Arrays.asList("image", "text"))
                        .build(),
                AgentSkill.builder()
                        .id("data-cleaning")
                        .name("数据清洗")
                        .description("处理缺失值、异常值、重复数据等")
                        .tags(Arrays.asList("cleaning", "preprocessing", "data"))
                        .inputModes(Arrays.asList("data", "text"))
                        .outputModes(Arrays.asList("data", "text"))
                        .build()
        );

        return AgentCard.builder()
                .name("数据分析智能体")
                .description("专业的数据分析智能体，支持数据加载、统计分析、可视化等功能")
                .url("http://localhost:8082")
                .provider(provider)
                .version("1.0.0")
                .capabilities(capabilities)
                .defaultInputModes(Arrays.asList("text", "file", "data"))
                .defaultOutputModes(Arrays.asList("text", "data", "image"))
                .skills(skills)
                .build();
    }
}
