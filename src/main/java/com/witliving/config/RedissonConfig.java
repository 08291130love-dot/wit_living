package com.witliving.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(RedisProperties redis){
        // 配置
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress((redis.isSsl() ? "rediss://" : "redis://") + redis.getHost() + ":" + redis.getPort())
                .setDatabase(redis.getDatabase());
        if (redis.getPassword() != null && !redis.getPassword().isEmpty()) {
            server.setPassword(redis.getPassword());
        }
        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}
