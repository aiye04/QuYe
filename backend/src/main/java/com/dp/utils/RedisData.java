package com.dp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * 缓存击穿，设计逻辑有效时间
 */
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
