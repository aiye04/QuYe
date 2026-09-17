package com.dp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:localhost}")
    private String host;

    @Value("${spring.redis.port:6379}")
    private String port;

    @Value("${spring.redis.password:}")
    private String password;

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();   //创建配置对象
        config.useSingleServer()    //使用单节点模式
                .setAddress("redis://" + host + ":" + port)   //Redis 地址（读 application.yaml）
                .setPassword(password.isEmpty() ? null : password);   //Redis 密码
        return Redisson.create(config); //创建RedissonClient对象
    }

}
