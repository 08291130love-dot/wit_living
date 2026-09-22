package com.witliving.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.witliving.config.RocketMqTopics;
import com.witliving.dto.Result;
import com.witliving.entity.VoucherOrder;
import com.witliving.mapper.VoucherOrderMapper;
import com.witliving.service.IVoucherOrderService;
import com.witliving.utils.RedisIdWorker;
import com.witliving.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * 秒杀接口服务：请求线程只完成 Redis 资格校验和 RocketMQ 投递。
 * 数据库扣库存、订单入库由消费者在独立事务中异步执行，从而削峰。
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    @Resource
    // 基于 Redis 自增序列生成分布式订单 ID，订单入库前也能先拥有唯一编号。
    private RedisIdWorker redisIdWorker;
    @Resource
    // 执行 Lua 脚本、维护秒杀库存和用户购买标记。
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    // 将预扣成功的订单发送到 RocketMQ，由消费者异步落库。
    private RocketMQTemplate rocketMQTemplate;

    // Redis 端原子校验库存和一人一单的脚本。
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    // MQ 投递失败时回补 Redis 预扣库存、移除用户购买标记的补偿脚本。
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        // 创建 Lua 脚本包装对象，并指定脚本在 resources 中的位置和返回值类型。
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        // 失败补偿脚本与主脚本分离，避免 Broker 不可用时库存被永久占用。
        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("seckill_rollback.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    @Override
    // 秒杀接口主流程：校验资格 → 投递消息 → 返回“订单处理中”的订单号。
    public Result seckillVoucher(Long voucherId) {
        // 登录拦截器已将当前用户放进 ThreadLocal，这里取得下单用户。
        Long userId = UserHolder.getUser().getId();
        // 提前生成订单号，并随 MQ 消息传给消费者。
        long orderId = redisIdWorker.nextId("order");

        // Lua 在 Redis 内原子完成库存判断、重复判断、库存预扣和用户标记。
        Long scriptResult = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                // 当前 Lua 只使用前两个参数；orderId 暂未在脚本中使用，保留它不会参与 Redis 预扣逻辑。
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        // null 视为系统异常；1 表示无库存；2 表示该用户已抢过。
        int result = scriptResult == null ? -1 : scriptResult.intValue();
        if (result != 0) {
            // 未通过资格校验时不发送消息，也不访问 MySQL。
            return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
        }

        // 组装可序列化的订单消息体。
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        try {
            // 同步等待 Broker 接收确认；但真正写 MySQL 仍由消费者异步完成。
            rocketMQTemplate.syncSend(RocketMqTopics.SECKILL_ORDER_TOPIC, order);
        } catch (Exception e) {
            // 发送异常时不能遗留 Redis 预扣，必须执行补偿脚本回滚资格。
            stringRedisTemplate.execute(
                    ROLLBACK_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(), userId.toString()
            );
            // 记录订单号和异常，便于运维排查消息投递失败。
            log.error("seckill order publish failed, orderId={}", orderId, e);
            return Result.fail("系统繁忙，请稍后重试");
        }
        // 返回订单号仅表示资格已受理，前端应通过订单查询确认最终落库结果。
        return Result.ok(orderId);
    }

}
