package com.dp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.dp.entity.User;
import com.dp.service.IUserService;
import com.dp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import cn.hutool.core.lang.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest
class BatchLoginTests {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void batchLoginAndSaveTokens() {
        // 1. 查询所有用户（1000 个）
        List<User> userList = userService.list();
        log.info("共查询到 {} 个用户", userList.size());

        // 2. 准备输出文件
        String filePath = "token.txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {

            for (User user : userList) {
                // 3. 生成 token
                String token = UUID.randomUUID().toString(true);

                // 4. 将 User 转为 UserDTO
                // 注意：UserDTO 可以自己定义，也可用 User 直接转 Map
                Map<String, Object> userMap = BeanUtil.beanToMap(user, new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

                // 5. 存入 Redis Hash
                String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
                stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
                stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

                // 6. 写入文件，一行一个 token
                writer.write(token);
                writer.newLine();

                log.info("用户 {} 登录成功，token = {}", user.getPhone(), token);
            }
            writer.flush();
            log.info("所有 token 已写入 {}", filePath);
            System.out.println("文件绝对路径: " + filePath);


        } catch (IOException e) {
            log.error("写入 token 文件失败", e);
        }
    }
}