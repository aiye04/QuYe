package com.dp.service.impl;

import com.dp.dto.Result;
import com.dp.entity.SeckillActivity;
import com.dp.mapper.SeckillActivityMapper;
import com.dp.service.ISeckillActivityService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class SeckillActivityServiceImpl extends ServiceImpl<SeckillActivityMapper, SeckillActivity> implements ISeckillActivityService {
    @Override
    public Result seckillActivity(Long voucherId) {
        return null;
    }


}
