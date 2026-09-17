package com.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.dp.dto.Result;
import com.dp.entity.ActivityOrder;
import com.dp.mapper.ActivityOrderMapper;
import com.dp.service.ISeckillActivityService;
import com.dp.service.IActivityOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.utils.RedisIdWorker;
import com.dp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class ActivityOrderServiceImpl extends ServiceImpl<ActivityOrderMapper, ActivityOrder> implements IActivityOrderService {

    @Resource
    private ISeckillActivityService seckillActivityService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>(); //  创建工具实例
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua")); // 告诉工具脚本在哪
        SECKILL_SCRIPT.setResultType(Long.class); //  告诉工具脚本执行后返回啥类型
    }
    //创建线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    //创建线程任务
    @PostConstruct
    public void init(){
        SECKILL_ORDER_EXECUTOR.submit(new ActivityOrderHandler());
    }
    @PreDestroy
    public void destroy(){
        SECKILL_ORDER_EXECUTOR.shutdownNow();
    }

    private class ActivityOrderHandler implements Runnable {
        private String queueName = "stream.orders";
        @Override
        public void run() {
            while(true){
                try {
                    //1.获取消息队列中的订单信息  读取XREADGROP GROUP
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),// 消费者组来自
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),// 读取数量和超时时间
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())// 从最后一个消费记录开始读取
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        //2.1不存在，说明没有订单信息，循环
                        continue;
                    }
                    //3.存在，获取订单成功，创建订单
                    MapRecord<String, Object, Object> record = list.get(0);// 3.1拿到第一条消息
                    Map<Object, Object> value = record.getValue();// 3.2获取订单信息
                    ActivityOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new ActivityOrder(), true);// 3.3将订单信息转对象

                    createActivityOrder(voucherOrder);// 4.创建订单,执行业务逻辑
                    //5.ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1", record.getId());
                } catch (Exception e) {
                    log.error("订单处理异常", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while(true){
                try {
                    //1.获取pending list中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),// 消费者组来自
                            StreamReadOptions.empty().count(1),// 读取数量和超时时间
                            StreamOffset.create(queueName, ReadOffset.from("0"))// 从最后一个消费记录开始读取
                    );
                    if (list == null || list.isEmpty()) {
                        //2.1不存在，说明没有订单信息，结束循环
                        break;
                    }
                    //3.存在，获取订单成功，创建订单
                    MapRecord<String, Object, Object> record = list.get(0);// 3.1拿到第一条消息
                    Map<Object, Object> value = record.getValue();// 3.2获取订单信息
                    ActivityOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new ActivityOrder(), true);// 3.3将订单信息转对象
                    createActivityOrder(voucherOrder);// 4.创建订单,执行业务逻辑
                    //5.ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1", record.getId());
                } catch (Exception e) {
                    log.error("处理Pending List订单处理异常", e);
                    try {
                        Thread.sleep(1000);   //TODO
                    } catch (InterruptedException interruptedException) {
                        throw new RuntimeException(interruptedException);
                    }
                }
            }
        }
    }
    // 4.1一人一单逻辑
    @Transactional
    public void createActivityOrder(ActivityOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            log.error("不允许重复下单！");
            return;
        }
        try {
            // 查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            //判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                log.error("不允许重复下单！");
                return;
            }
          // 5.扣减库存
            boolean success = seckillActivityService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
            // 5.1判断更新是否成功
            if (!success) {
            log.error("库存不足！");
            return;   // 扣减失败也要返回
        }
            //创建订单
            save(voucherOrder);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }

    @Override
    public Result seckillActivity(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //生成订单id
        long orderId = redisIdWorker.nextId("order");
        //1.执行seckill.lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        //2.判断结果是否为0
        int r = result.intValue();
        if(r != 0){
            //2.1. 不为0,返回错误信息
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //4. 返回订单id
        return Result.ok(orderId);
    }
    //阻塞队列旧代码
/*    @Override
    public Result seckillActivity(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //1.执行seckill.lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
                );
        //2.判断结果是否为0
        int r = result.intValue();
        if(r != 0){
            //2.1. 不为0,返回错误信息
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        ActivityOrder voucherOrder = new ActivityOrder();
        //2.2. 为0,返回订单id,将订单id存入阻塞队列
        long orderId = redisIdWorker.nextId("order");
        // 2.4.用户id
        voucherOrder.setUserId(userId);
        //2.3. 订单id
        voucherOrder.setId(orderId);
        //2.5.活动名额id
        voucherOrder.setVoucherId(voucherId);
        //3. 保存到阻塞队列
        orderTask.add(voucherOrder);
        //获取代理对象（事务）
        proxy = (IActivityOrderService) AopContext.currentProxy();
        //4. 返回订单id
        return Result.ok(orderId);
    }*/
    /*@Override
    public Result seckillActivity(Long voucherId) {
        // 1.查询活动名额
        SeckillActivity seckillActivity = seckillActivityService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (seckillActivity.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (seckillActivity.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (seckillActivity.getStock() <= 0) {
            return Result.fail("库存不足！");
        }
        //4.1.1 获取当前用户
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()){
            //创建分布式锁对象
//            SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
            RLock lock = redissonClient.getLock("lock:order:" + userId);
            //尝试获取锁
            boolean isLock = lock.tryLock();
            //判断
            if (!isLock) {
                return Result.fail("请勿重复下单！");
            }
            try {
                //获取代理对象（事务）
                IActivityOrderService proxy = (IActivityOrderService) AopContext.currentProxy();
                // 执行业务逻辑（必须在锁的保护范围内）
                return proxy.createActivityOrder(voucherId);
            } finally {
                //释放锁
                lock.unlock();
            }
        }
    }*/

}
