package com.dp.service;

import com.dp.dto.Result;
import com.dp.entity.Venue;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVenueService extends IService<Venue> {

    Result queryById(Long id) throws InterruptedException;


    Result update(Venue shop);

    Result queryVenueByType(Integer typeId, Integer current, Double x, Double y);
}

