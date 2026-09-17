package com.dp;

import com.dp.entity.Venue;
import com.dp.service.impl.VenueServiceImpl;
import com.dp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.dp.utils.RedisConstants.VENUE_GEO_KEY;

@Slf4j
@SpringBootTest
class QuYeApplicationTests {
    @Resource
    private VenueServiceImpl venueService;
    @Resource
    private RedisIdWorker redisIdWorker;
    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void testVenue(){
        venueService.setLogicalExpire(1L, 10L);
    }
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(500);
        Runnable task = () -> {
            try {
                for (int i = 0; i < 100; i++) {
                    long id = redisIdWorker.nextId("order");
                    System.out.println(id);
                }
            } finally {
                latch.countDown();  // 每个任务结束减一
            }
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        log.info("time: {}", end - begin);
    }
    @Test
    void loadVenueData() {
        // 1.查询场馆信息
        List<Venue> venueList = venueService.list();
        // 2.把场馆分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Venue>> map = venueList.stream().collect(Collectors.groupingBy(Venue::getTypeId));
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Venue>> entry : map.entrySet()){
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            // 3.2.获取同类型的场馆的集合
            List<Venue> value = entry.getValue();
            String key = VENUE_GEO_KEY + typeId;
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (Venue shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),// 场馆id
                        new Point(shop.getX(), shop.getY())));  // 经度和纬度
            }
            // 3.3.写入redis GEOADD key 经度 纬度 member
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }
}
