package com.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.dp.dto.Result;
import com.dp.dto.UserDTO;
import com.dp.entity.Follow;
import com.dp.mapper.FollowMapper;
import com.dp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private  StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserServiceImpl userService;

    /**
     * 关注用户操作
     * 使用isFollow方法判断是否关注，无则进行关注操作
     * @param followUserId 被关注用户id
     * @param isFollow 是否关注
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取登录用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //判断该取消关注还是关注
        if(isFollow){
            //关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // 把关注用户的id，放入redis的set集合 sadd userId followerUserId
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }else{
            //取消关注  delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }
    /**
     * 判断是否关注通过用户id
     * 在MySQL查询tb_follow表的user_id记录是否有对应的follow_user_id，有则已关注，否则未关注
     * @param followUserId 被关注用户id
     * @return 返回是否关注了该用户
     */
    @Override
    public Result isFollow(Long followUserId) {
        //获取登录用户id
        Long userId = UserHolder.getUser().getId();
        //判断是否关注  select count(*) from tb_follow where user_id = ? and follow_user_id = ?
        Integer count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId).count();    //统计满足条件的记录数
        return Result.ok(count>0);
    }

    @Override
    public Result commonFollow(Long id) {
        //获取登录用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        String key2 = "follows:" + id;
        //获取两个set的交集 SINTER follows:{userId} follows:{id}
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        //没有交集时返回的是空 Set（不是 null），必须返回空列表，不能返回 null
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //解析共同关注id
        List<Long> ids = intersect.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        //查询共同关注用户信息
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
