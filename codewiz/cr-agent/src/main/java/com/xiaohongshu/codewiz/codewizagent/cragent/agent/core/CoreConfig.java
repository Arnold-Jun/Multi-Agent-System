package com.xiaohongshu.codewiz.codewizagent.cragent.agent.core;

import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/20 18:08
 */
@Configuration
public class CoreConfig {

    @Bean
    public BaseCheckpointSaver createCheckpointSaver() {
        return new MemorySaver();
    }
}
