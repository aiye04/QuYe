package com.dp.controller;


import com.dp.dto.Result;
import com.dp.entity.Activity;
import com.dp.service.IActivityService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/voucher")
public class ActivityController {

    @Resource
    private IActivityService activityService;

    /**
     * 新增普通券
     * @param voucher 活动名额信息
     * @return 活动名额id
     */
    @PostMapping
    public Result addActivity(@RequestBody Activity voucher) {
        activityService.save(voucher);
        return Result.ok(voucher.getId());
    }
    /**
     * 新增秒杀活动
     * @param voucher 活动名额信息，包含秒杀信息
     * @return 活动名额id
     */
    @PostMapping("seckill")
    public Result addSeckillActivity(@RequestBody Activity voucher) {
        activityService.addSeckillActivity(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 查询场馆的活动名额列表
     * @param shopId 场馆id
     * @return 活动名额列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryActivityOfVenue(@PathVariable("shopId") Long shopId) {
       return activityService.queryActivityOfVenue(shopId);
    }
}
