-- RocketMQ 发送抛出异常后执行：回补 Redis 预扣库存，并移除该用户的购买标记。
-- 该脚本只补偿 Redis；它无法覆盖“Broker 已收到消息但发送端未收到确认”的不确定场景。
local voucherId = ARGV[1]
local userId = ARGV[2]
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

redis.call('incrby', stockKey, 1)
redis.call('srem', orderKey, userId)
return 0
