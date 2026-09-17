package com.dp.controller;



import com.dp.dto.Result;
import com.dp.service.IPostService;
import com.dp.service.IFollowService;
import com.dp.service.IUserService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;


    /**
     * 关注用户判断操作
     * @param followUserId 被关注用户id
     * @param isFollow 是否关注
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow)
    {
        return followService.follow(followUserId,isFollow);
    }
    /**
     * 取消关注操作
     * @param followUserId 被关注用户id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId){
        return followService.isFollow(followUserId);
    }
    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable("id") Long id){
        return followService.commonFollow(id);
    }


}
