package com.dp.service.impl;

import com.dp.entity.PostComments;
import com.dp.mapper.PostCommentsMapper;
import com.dp.service.IPostCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class PostCommentsServiceImpl extends ServiceImpl<PostCommentsMapper, PostComments> implements IPostCommentsService {

}
