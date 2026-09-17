package com.dp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dp.entity.Activity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 去野
 * @since 2021-12-22
 */
public interface ActivityMapper extends BaseMapper<Activity> {

    List<Activity> queryActivityOfVenue(@Param("shopId") Long shopId);
}
