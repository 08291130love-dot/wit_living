package com.witliving.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.witliving.entity.VoucherOrder;
import com.witliving.mapper.VoucherOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 秒杀订单数据库事务边界。
 * 必须由消费者通过另一个 Spring Bean 调用，避免同类内部调用绕过事务代理。
 */
@Slf4j
@Service
public class VoucherOrderTransactionService {
    @Resource
    // MyBatis-Plus 服务：对秒杀库存表执行带条件的原子更新。
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    // Mapper：查询并持久化优惠券订单。
    private VoucherOrderMapper voucherOrderMapper;

    @Transactional
    // 一个事务内完成查重、扣减数据库库存和写订单，任一步失败都会回滚。
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 从消息体中取出业务唯一键的两个维度。
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 消费端幂等校验：RocketMQ 至少一次投递时，已有订单的重复消息直接忽略。
        Integer count = voucherOrderMapper.selectCount(new QueryWrapper<VoucherOrder>()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId));
        if (count != null && count > 0) {
            // 正常幂等分支，不抛异常，表示这条重复消息已被安全处理。
            log.info("duplicate seckill message ignored, userId={}, voucherId={}", userId, voucherId);
            return;
        }

        // WHERE stock > 0 是数据库层防止库存被扣成负数的最终条件。
        boolean stockUpdated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!stockUpdated) {
            // 抛异常会触发事务回滚，也会让 RocketMQ 对这条消息执行重试策略。
            throw new IllegalStateException("database stock is insufficient");
        }
        // 仅在库存扣减成功后插入订单；数据库的 (user_id, voucher_id) 唯一索引是最后一道一人一单约束。
        voucherOrderMapper.insert(voucherOrder);
    }
}
