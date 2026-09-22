package com.witliving.utils;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.witliving.entity.Shop;
import com.witliving.mapper.ShopMapper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;

/**
 * In-memory Bloom filter. A negative result definitely means the shop does not
 * exist; a positive result is allowed to continue to Redis/MySQL.
 */
@Component
public class ShopBloomFilter {
    private static final int MIN_EXPECTED_INSERTIONS = 1_000;
    private volatile BloomFilter<Long> filter;

    @Resource
    private ShopMapper shopMapper;

    @PostConstruct
    public void initialize() {
        List<Shop> shops = shopMapper.selectList(null);
        filter = BloomFilter.create(
                Funnels.longFunnel(),
                Math.max(MIN_EXPECTED_INSERTIONS, shops.size() * 2),
                0.01
        );
        for (Shop shop : shops) {
            filter.put(shop.getId());
        }
    }

    public boolean mightContain(Long shopId) {
        return shopId != null && filter.mightContain(shopId);
    }

    public void put(Long shopId) {
        if (shopId != null) {
            filter.put(shopId);
        }
    }
}
