package com.dp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 生成全局唯一订单号工具类
 */
@Component
public class RedisIdWorker {

    private final long BEGIN_TIMESTAMP = 1789331791L;
    private final long COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nextSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nextSecond - BEGIN_TIMESTAMP;
        //2.生成序列号
        //2.1获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:sn" + keyPrefix + ":" + date);
        //3.拼接
        return timestamp << COUNT_BITS | count;
    }
}
