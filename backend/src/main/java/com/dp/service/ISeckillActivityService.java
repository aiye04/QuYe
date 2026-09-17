package com.dp.service;

import com.dp.dto.Result;
import com.dp.entity.SeckillActivity;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ISeckillActivityService extends IService<SeckillActivity> {

    Result seckillActivity(Long voucherId);
}
