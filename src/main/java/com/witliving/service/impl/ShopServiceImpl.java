package com.witliving.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.witliving.dto.Result;
import com.witliving.entity.Shop;
import com.witliving.mapper.ShopMapper;
import com.witliving.service.IShopService;
import com.witliving.service.CacheInvalidationProducer;
import com.witliving.utils.CacheClient;
import com.witliving.utils.ShopBloomFilter;
import com.witliving.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.witliving.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
// 商户领域服务：负责详情缓存、商户更新失效、分类/GEO 查询等核心读写逻辑。
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    // Redis 二级缓存与 GEO 查询客户端。
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    // 封装空值缓存、逻辑过期和互斥锁重建策略。
    private CacheClient cacheClient;

    @Resource
    // JVM 本地一级缓存，优先命中以减少 Redis 网络访问。
    private Cache<Long, Shop> shopLocalCache;

    @Resource
    // 负判定可直接拦截无效商户 ID，降低缓存穿透压力。
    private ShopBloomFilter shopBloomFilter;

    @Resource
    // 更新后执行 Redis 删除并发布 RocketMQ 二次删除补偿消息。
    private CacheInvalidationProducer cacheInvalidationProducer;

    @Override
    // 商户详情读取链路：BloomFilter → Caffeine(L1) → Redis 逻辑过期缓存(L2) → MySQL。
    public Result queryById(Long id) {
        // Bloom Filter 返回 false 时可确定不存在，直接结束请求。
        if (!shopBloomFilter.mightContain(id)) {
            return Result.fail("店铺不存在！");
        }
        // Caffeine 是单实例 L1；Redis 是多实例共享的 L2 逻辑过期缓存。
        Shop shop = shopLocalCache.getIfPresent(id);
        if (shop == null) {
            // L1 未命中时调用缓存组件；组件内部按需要查询数据库并回填 Redis。
            shop = cacheClient.queryWithLogicalExpire(
                    CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            if (shop != null) {
                // 将有效商户写回本地缓存，使同一实例的热点读直接命中内存。
                shopLocalCache.put(id, shop);
            }
        }

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        // 统一包装成功响应。
        return Result.ok(shop);
    }

    @Override
    @Transactional
    // 更新顺序：更新 MySQL → 清本地缓存 → 清 Redis 并投递异步补偿消息。
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 先持久化数据库，数据库是商户信息的事实来源。
        updateById(shop);
        // 当前实例立即失效 L1，避免本机继续返回旧对象。
        shopLocalCache.invalidate(id);
        // 直删 Redis 后发送补偿消息；重复 DEL 同一 Key 是幂等的。
        cacheInvalidationProducer.evictShopAfterUpdate(id);
        // 新增或更新商户均写入 Bloom Filter，避免新 ID 被负判定误拦截。
        shopBloomFilter.put(id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }
}
