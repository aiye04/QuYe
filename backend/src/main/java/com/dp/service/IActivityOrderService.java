package com.dp.service;

import com.dp.dto.Result;
import com.dp.entity.ActivityOrder;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IActivityOrderService extends IService<ActivityOrder> {

    Result seckillActivity(Long voucherId);

    void createActivityOrder(ActivityOrder voucherOrder);
}
