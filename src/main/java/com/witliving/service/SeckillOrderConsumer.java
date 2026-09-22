package com.witliving.service;

import com.witliving.config.RocketMqTopics;
import com.witliving.entity.VoucherOrder;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * RocketMQ 秒杀订单消费者：将“抢购资格”异步转换为数据库订单。
 * 消息可能重复投递，因此此类只负责串行化同一用户的处理；最终幂等由事务服务和数据库唯一索引保障。
 */
@Slf4j
@Service
@RocketMQMessageListener(
        topic = RocketMqTopics.SECKILL_ORDER_TOPIC,
        consumerGroup = "wit_living-seckill-order-consumer",
        maxReconsumeTimes = 3
)
public class SeckillOrderConsumer implements RocketMQListener<VoucherOrder> {
    @Resource
    // Redisson 客户端，用于在多个服务实例之间竞争同一把 Redis 分布式锁。
    private RedissonClient redissonClient;
    @Resource
    // 独立 Bean，确保调用能够经过 Spring 代理，从而使 @Transactional 生效。
    private VoucherOrderTransactionService transactionService;

    @Override
    // Broker 将一条 VoucherOrder 消息投递给本消费者时调用此方法。
    public void onMessage(VoucherOrder order) {
        // 锁按用户维度隔离：同一用户的重复消息互斥，不同用户仍可并发创建订单。
        RLock lock = redissonClient.getLock("lock:order:" + order.getUserId());
        // 不等待锁；抢锁失败则抛异常，让 RocketMQ 将该消息按失败处理并重试。
        if (!lock.tryLock()) {
            // 达到 maxReconsumeTimes 后，RocketMQ 会将持续失败的消息转入死信队列。
            throw new IllegalStateException("order lock is busy");
        }
        try {
            // 在事务中查重、条件扣库存并插入订单；任一步异常都会使事务回滚。
            transactionService.createVoucherOrder(order);
        } finally {
            // 无论成功还是异常都释放当前线程持有的锁，避免同一用户后续消息长期阻塞。
            lock.unlock();
        }
    }
}
