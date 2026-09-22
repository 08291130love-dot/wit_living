package com.witliving.config;

/** RocketMQ Topic 常量：集中管理，避免生产者和消费者手写字符串导致订阅不一致。 */
public final class RocketMqTopics {
    private RocketMqTopics() {
    }

    // 秒杀资格校验通过后发送订单对象，由消费者异步写 MySQL。
    public static final String SECKILL_ORDER_TOPIC = "wit_living-seckill-order";
    // 商户更新后发送 shopId，由消费者执行幂等的 Redis 二次删除。
    public static final String CACHE_INVALIDATION_TOPIC = "wit_living-cache-invalidate";
}
