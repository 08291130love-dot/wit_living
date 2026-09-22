package com.witliving.service;

import com.witliving.config.RocketMqTopics;
import com.witliving.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/** 商户更新后的缓存失效生产者：直删 Redis，再发布一次幂等删除补偿。 */
@Slf4j
@Service
public class CacheInvalidationProducer {
    @Resource
    // 操作共享的 Redis 二级缓存。
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    // 向缓存失效 Topic 投递商户 ID。
    private RocketMQTemplate rocketMQTemplate;

    // 由 ShopService.update() 在 updateById() 返回后调用；该调用仍发生在外层事务提交前。
    public void evictShopAfterUpdate(Long shopId) {
        // 拼出当前商户详情的 Redis Key。
        String key = RedisConstants.CACHE_SHOP_KEY + shopId;
        // 第一次立即删除，尽快阻止请求继续读取旧缓存。
        stringRedisTemplate.delete(key);
        try {
            // 发布商户 ID；消费者会对同一 Key 再删一次，DEL 本身是幂等操作。
            rocketMQTemplate.syncSend(RocketMqTopics.CACHE_INVALIDATION_TOPIC, String.valueOf(shopId));
        } catch (Exception e) {
            // 第一次删除已经完成；此处记录“补偿消息缺失”，便于后续监控与人工处理。
            log.error("shop cache compensation message publish failed, shopId={}", shopId, e);
        }
    }
}
