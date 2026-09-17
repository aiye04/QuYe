package com.dp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dp.entity.Venue;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.websocket.Util;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.dp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private  final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);  // 创建一个固定大小的线程池

    //     * 方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit unit){
        // 防止缓存雪崩TTL延长 5% ~ 10%
        long finalTime = Math.round(time * (1.05 + Math.random() * 0.05));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), finalTime, unit);
    }
//     * 方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
//     * 方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1.从 Redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        //判断是否是空值
        if (json != null) {
            return null;
        }
        // 3.未命中，查询数据库
        R r = dbFallback.apply(id);
        // 4.数据库也没有，返回失败信息
        if (r == null) {
            //空值写入Redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 5.写入缓存,设置缓存时间
        this.set(key,r,time,unit);

        return r;
    }
//     * 方法4：根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type,Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1.从 Redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.没命中缓存，返回
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 3.命中，将json反序列化为Venue对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);   //把外层JSON转成RedisData对象,拿到了expireTime和data
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);  //data字段转成type对象
        LocalDateTime expireTime = redisData.getExpireTime();   //获取过期时间
        // 4.判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.未过期返回type信息
            return r;
        }
        // 6.过期，缓存重建
        // 7.获取互斥锁
        String lockKey = LOCK_VENUE_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 7.1判断是否获取锁成功
        if(isLock){
            //DoubleCheck
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                RedisData newRedisData = JSONUtil.toBean(json, RedisData.class);
                LocalDateTime newExpireTime = newRedisData.getExpireTime();
                //如果新的逻辑过期时间还未到，说明新线程已经重建了缓存,返回type信息
                if (newExpireTime.isAfter(LocalDateTime.now())) {
                    unlock(lockKey);
                    return JSONUtil.toBean((JSONObject) newRedisData.getData(), type);
                }
            }
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);// 无论成功还是异常，释放锁
                }
            });
        }
        // 8.返回过期的场馆信息
        return r;
    }
    public boolean tryLock(String key) {
        // 锁必须用较短的过期时间兜底，否则线程异常退出后锁会长时间无法释放
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_VENUE_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

}
