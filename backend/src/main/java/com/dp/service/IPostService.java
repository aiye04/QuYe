package com.dp.service;

import com.dp.dto.Result;
import com.dp.entity.Post;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IPostService extends IService<Post> {

    Result queryHotPost(Integer current);

    Result queryPostById(Long id);

    Result queryPostUser(Post blog);

    Result likePost(Long id);

    Result queryPostLikes(Long id);

    Result savePost(Post blog);

    Result queryPostByFollow(Long max, Integer offset);
}
