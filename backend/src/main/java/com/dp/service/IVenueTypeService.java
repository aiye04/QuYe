package com.dp.service;

import com.dp.entity.VenueType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IVenueTypeService extends IService<VenueType> {

    List<VenueType> queryByType();
}
