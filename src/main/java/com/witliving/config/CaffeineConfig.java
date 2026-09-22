package com.witliving.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.witliving.entity.Shop;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/** L1 cache for hot shop details; Redis remains the shared L2 cache. */
@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<Long, Shop> shopLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }
}
