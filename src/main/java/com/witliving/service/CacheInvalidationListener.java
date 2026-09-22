package com.witliving.service;

import com.witliving.config.RocketMqTopics;
import com.witliving.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/** RocketMQ 消费者：收到商户 ID 后再次删除 Redis 二级缓存，作为异步补偿。 */
@Slf4j
@Service
@RocketMQMessageListener(
        topic = RocketMqTopics.CACHE_INVALIDATION_TOPIC,
        consumerGroup = "wit_living-cache-invalidation-consumer",
        maxReconsumeTimes = 3
)
public class CacheInvalidationListener implements RocketMQListener<String> {
    @Resource
    // 用于执行对商户缓存 Key 的删除操作。
    private StringRedisTemplate stringRedisTemplate;

    @Override
    // Broker 投递一条商户 ID 消息时调用；重复调用同样安全。
    public void onMessage(String message) {
        // 消息体只携带商户 ID，转换后用于拼接缓存 Key。
        Long shopId = Long.valueOf(message);
        // Key 已不存在时 DEL 仍安全，因此可承受消息重试和重复投递。
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shopId);
        // Debug 日志记录补偿链路完成，生产中可用于排查 Topic 堆积。
        log.debug("compensation cache eviction completed, shopId={}", shopId);
    }
}
