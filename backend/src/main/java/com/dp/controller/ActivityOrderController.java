package com.dp.controller;


import com.dp.dto.Result;
import com.dp.entity.SeckillActivity;
import com.dp.service.ISeckillActivityService;
import com.dp.service.IActivityOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/voucher-order")
public class ActivityOrderController {
    @Resource
    private IActivityOrderService activityService;

    @PostMapping("seckill/{id}")
    public Result seckillActivity(@PathVariable("id") Long voucherId) {
        return activityService.seckillActivity(voucherId);
    }
}
