package com.xiaohongshu.codewiz.codewizagent.cragent.a2a;

import com.xiaohongshu.codewiz.a2acore.spec.AgentAuthentication;
import com.xiaohongshu.codewiz.a2acore.spec.AgentCapabilities;
import com.xiaohongshu.codewiz.a2acore.spec.AgentCard;
import com.xiaohongshu.codewiz.a2acore.spec.AgentSkill;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * Cr Agent Card
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/12 11:41
 */
@Configuration
public class CrAgentCard {

    @Bean
    public AgentCard createAgentCard() {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setPushNotifications(true);
        capabilities.setStreaming(true);
        capabilities.setStateTransitionHistory(true);
        AgentSkill skill = AgentSkill.builder()
                .id("create_cr")
                .name("用于创建云效Cr的工具")
                .description("帮助用户完成创建云效Cr的一系列流程")
                .tags(Arrays.asList("云效CR", "yunxiao code_review"))
                .examples(Collections.singletonList("帮我创建一个云效CR"))
                .inputModes(List.of("text", "data"))
                .outputModes(List.of("text", "data"))
                .build();
        AgentCard agentCard = new AgentCard();
        agentCard.setName("CrAgent");
        agentCard.setDescription("CrAgent");
        agentCard.setUrl("http://127.0.0.1:" + 8089);
        agentCard.setVersion("1.0.1");
        agentCard.setCapabilities(capabilities);
        agentCard.setSkills(Collections.singletonList(skill));
        agentCard.setDefaultInputModes(List.of("text", "data"));
        agentCard.setDefaultOutputModes(List.of("text", "data", "file"));
        return agentCard;
    }
}
