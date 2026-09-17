package com.dp.service;

import com.dp.dto.Result;
import com.dp.entity.Activity;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 去野
 * @since 2021-12-22
 */
public interface IActivityService extends IService<Activity> {

    Result queryActivityOfVenue(Long shopId);

    void addSeckillActivity(Activity voucher);

}
