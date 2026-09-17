package com.dp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 5L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 1800L;

    public static final Long CACHE_NULL_TTL = 1L;

    public static final Long CACHE_VENUE_TTL = 30L;
    public static final String CACHE_VENUE_KEY = "cache:venue:";

    public static final String CACHE_VENUE_TYPE_KEY = "cache:venue:type:";
    public static final Long CACHE_VENUE_TYPE_TTL = 300L;

    public static final String LOCK_VENUE_KEY = "lock:venue:";
    public static final Long LOCK_VENUE_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String POST_LIKED_KEY = "post:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String VENUE_GEO_KEY = "venue:geo:";
    public static final String USER_SIGN_KEY = "sign:";

}
