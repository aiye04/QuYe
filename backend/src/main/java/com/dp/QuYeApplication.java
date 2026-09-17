package com.dp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.dp.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class QuYeApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuYeApplication.class, args);
    }

}
