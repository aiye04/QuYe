package com.dp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.dto.Result;
import com.dp.entity.Activity;
import com.dp.mapper.ActivityMapper;
import com.dp.entity.SeckillActivity;
import com.dp.service.ISeckillActivityService;
import com.dp.service.IActivityService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static com.dp.utils.RedisConstants.SECKILL_STOCK_KEY;

@Service
public class ActivityServiceImpl extends ServiceImpl<ActivityMapper, Activity> implements IActivityService {

    @Resource
    private ISeckillActivityService seckillActivityService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryActivityOfVenue(Long shopId) {
        // 查询活动名额信息
        List<Activity> activities = getBaseMapper().queryActivityOfVenue(shopId);
        // 返回结果
        return Result.ok(activities);
    }

    @Override
    @Transactional
    public void addSeckillActivity(Activity voucher) {
        // 保存活动名额
        save(voucher);
        // 保存秒杀信息
        SeckillActivity seckillActivity = new SeckillActivity();
        seckillActivity.setVoucherId(voucher.getId());
        seckillActivity.setStock(voucher.getStock());
        seckillActivity.setBeginTime(voucher.getBeginTime());
        seckillActivity.setEndTime(voucher.getEndTime());
        seckillActivityService.save(seckillActivity);
        // 保存秒杀库存到Redis中
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + seckillActivity.getVoucherId(), seckillActivity.getStock().toString());
    }

}
