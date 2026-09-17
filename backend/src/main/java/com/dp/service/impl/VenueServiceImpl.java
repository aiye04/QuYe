package com.dp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dp.dto.Result;
import com.dp.entity.Venue;
import com.dp.mapper.VenueMapper;
import com.dp.service.IVenueService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.utils.CacheClient;
import com.dp.utils.RedisData;
import com.dp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.dp.utils.RedisConstants.*;

@Service
public class VenueServiceImpl extends ServiceImpl<VenueMapper, Venue> implements IVenueService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    /**
     * 根据id查询场馆
     */
    @Override
    public Result queryById(Long id){
        //调用缓存穿透
/*        Venue shop = cacheClient.queryWithPassThrough(
                CACHE_VENUE_KEY, id, Venue.class, this::getById,//shopId-> getById(shopId) lambda前
                CACHE_VENUE_TTL, TimeUnit.MINUTES);*/
        //互斥锁解决缓存击穿
        Venue shop = queryWithMutex(id);
        //逻辑过期解决缓存击穿
//        Venue shop = cacheClient.queryWithLogicalExpire(
//                CACHE_VENUE_KEY, id, Venue.class, this::getById, CACHE_VENUE_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("场馆不存在");
        }
        return Result.ok(shop);
    }
    private Venue queryWithMutex(Long id){
        String key = CACHE_VENUE_KEY + id;

        // 1.从 Redis 查询缓存
        String venueJson = stringRedisTemplate.opsForValue().get(key);
        // 2.命中缓存，反序列化后返回
        if (StrUtil.isNotBlank(venueJson)) {
            return JSONUtil.toBean(venueJson, Venue.class);
        }
        //判断是否是空值
        if (venueJson != null) {
            return null;
        }
        // 3.未命中
        // 3.1获取互斥锁
        String lockKey = LOCK_VENUE_KEY + id;
        Venue shop = null;
        // 记录本线程是否真正拿到了锁：只有拿到锁才允许释放，否则会把别人的锁删掉，互斥就失效了
        boolean isLocked = false;
        try {
            isLocked = cacheClient.tryLock(lockKey);
            // 3.2判断是否获取成功
            if (!isLocked) {
                // 3.3获取锁失败，休眠
                Thread.sleep(200);
                return queryWithMutex(id);  // 递归调用
            }
            // 3.3.1 DoubleCheck 二次校验缓存
            venueJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(venueJson)) {
                return JSONUtil.toBean(venueJson, Venue.class);
            }
            // 3.4获取锁成功，查询数据库
            shop = getById(id);
            // 4.数据库没有，返回失败信息
            if (shop == null) {
                //空值写入Redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 5.写入缓存,设置缓存时间
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
                    CACHE_VENUE_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }finally {
            // 6.释放锁：只有拿到锁的线程才能释放
            if (isLocked) {
                cacheClient.unlock(lockKey);
            }
        }
        // 6.返回成功
        return shop;
    }
    public void setLogicalExpire(Long id, Long expireSeconds) {
        //1.获取场馆id
        Venue shop = getById(id);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.缓存写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_VENUE_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 更新场馆信息
     */
    @Override
    public Result update(Venue shop) {
        //获取场馆id
        Long id = shop.getId();
        //判断是否为空
        if (id == null) {
            return Result.fail("场馆id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_VENUE_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryVenueByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if(x == null || y == null) {
            Page<Venue> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = VENUE_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key,    //指定key
                        GeoReference.fromCoordinate(x, y),//以xy为圆心
                        new Distance(5000),         //方圆5km
                        RedisGeoCommands                 //Redis地理位置命令类
                                .GeoRadiusCommandArgs   //查询地理半径命令参数类
                                .newGeoRadiusArgs()     //新建地理半径参数方法
                                .includeDistance().limit(end) //包含距离，限制结果数量
                );
        // 4.解析出id
        if (results == null){
            return Result.fail("查询失败");
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list =
                results.getContent();//获取场馆信息（场馆ID、经纬度、距离）
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        //4.1.截取分页范围
        ArrayList<Long> ids = new ArrayList<>(list.size()); // 把各个场馆的id放入list
        Map<String, Distance> distanceMap = new HashMap<>(list.size());//把各个场馆的距离放入map
        list.stream().skip(from).limit(end - from).forEach(result -> {
            //4.2.获取id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            //4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Venue
        String idStr = StrUtil.join(",", ids);
        List<Venue> venues = query().in("id", ids)
                .last("ORDER BY FIELD(id, " + idStr + ")").list();
        //6.封装场馆的距离
        for(Venue shop : venues) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 返回
        return Result.ok(venues);
    }
}

