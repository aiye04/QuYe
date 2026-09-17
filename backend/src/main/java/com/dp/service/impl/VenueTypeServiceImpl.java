package com.dp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.dp.dto.Result;
import com.dp.entity.VenueType;
import com.dp.mapper.VenueTypeMapper;
import com.dp.service.IVenueTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.dp.utils.RedisConstants.*;

@Service
public class VenueTypeServiceImpl extends ServiceImpl<VenueTypeMapper, VenueType> implements IVenueTypeService {

    private final StringRedisTemplate stringRedisTemplate;

    public VenueTypeServiceImpl(StringRedisTemplate stringRedisTemplate, VenueTypeMapper shopTypeMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     *根据场馆类型查询
     */
    @Override
    public List<VenueType> queryByType() {
        String key = CACHE_VENUE_TYPE_KEY;
        //1.从redis查询缓存
        String venueTypesJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存中是否存在
        if (StrUtil.isNotBlank(venueTypesJson)) {
            //3.存在，把 JSON 字符串反序列化成 List<VenueType>
            return JSONUtil.toList(venueTypesJson, VenueType.class);
        }
        //3.1 命中的是之前写入的空值（缓存穿透保护），直接返回空列表，不要再查数据库
        if (venueTypesJson != null) {
            return Collections.emptyList();
        }
        //4.不存在，从数据库查询
        List<VenueType> typesList = query().orderByAsc("sort").list();
        //5.数据库不存在，返回错误信息
        if (typesList == null||typesList.isEmpty()) {
            //缓存空字符串
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Collections.emptyList();
        }
        //6.将查询结果写入Redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(typesList),CACHE_VENUE_TYPE_TTL, TimeUnit.MINUTES);//
        return typesList;
    }
}
