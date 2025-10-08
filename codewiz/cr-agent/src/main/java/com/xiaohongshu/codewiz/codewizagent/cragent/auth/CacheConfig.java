package com.xiaohongshu.codewiz.codewizagent.cragent.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.xiaohongshu.infra.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SHORT_FREQUENT_CACHE_NAME = "shortFrequentCache";

    public static final String LONG_FREQUENT_CACHE_NAME = "longFrequentCache";

    @Bean(name = SHORT_FREQUENT_CACHE_NAME)
    public Cache<Object, Object> getCache() {
        return CacheBuilder.newBuilder(SHORT_FREQUENT_CACHE_NAME, () -> 50)
                .maximumSize(10000L)
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .build();
    }

    @Bean(name = LONG_FREQUENT_CACHE_NAME)
    public Cache<Object, Object> getLongCache() {
        return CacheBuilder.newBuilder(LONG_FREQUENT_CACHE_NAME, () -> 50)
                .maximumSize(10000L)
                .expireAfterWrite(60 * 60, TimeUnit.SECONDS)
                .build();
    }
}