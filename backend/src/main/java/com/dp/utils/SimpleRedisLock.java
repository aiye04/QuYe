package com.dp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT; // 1. 声明工具
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>(); // 2. 创建工具实例
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua")); // 3. 告诉工具脚本在哪
        UNLOCK_SCRIPT.setResultType(Long.class); // 4. 告诉工具脚本执行后返回啥类型
    }

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name){
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 尝试获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //调用Lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
        //获取线程标识
        String threadId = KEY_PREFIX + Thread.currentThread().getId();
        //验证线程标识
        if (threadId.equals(stringRedisTemplate.opsForValue().get(KEY_PREFIX + name))) {
            //释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name + threadId);
        }
    }
}
