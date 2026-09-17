package com.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dp.dto.Result;
import com.dp.dto.ScrollResult;
import com.dp.dto.UserDTO;
import com.dp.entity.Post;
import com.dp.entity.Follow;
import com.dp.entity.User;
import com.dp.mapper.PostMapper;
import com.dp.service.IPostService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.service.IFollowService;
import com.dp.service.IUserService;
import com.dp.utils.SystemConstants;
import com.dp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.dp.utils.RedisConstants.POST_LIKED_KEY;
import static com.dp.utils.RedisConstants.FEED_KEY;

@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements IPostService {

    @Resource
    private IUserService userService;
    @Resource
    private IPostService postService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    /**
     * 查询热门动态列表
     * 按点赞数降序分页查询，并为每条动态补充当前登录用户的点赞状态
     * @param current 当前页码
     * @return 热门动态分页结果
     */
    @Override
    public Result queryHotPost(Integer current) {
        // 根据用户查询
        Page<Post> page = postService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Post> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryPostUser(blog);
            isPostLiked(blog);  // 补充动态点赞状态
        });
        return Result.ok(records);
    }
    /**
     * 根据 id 查询动态详情
     * 查询动态信息，并补充当前登录用户的点赞状态
     * @param id 动态 id
     * @return 动态详情结果
     */
    @Override
    public Result queryPostById(Long id) {
        // 查询blog
        Post blog = getById(id);
        if (blog == null) {
            return Result.fail("动态不存在");
        }
        queryPostUser(blog);
        isPostLiked(blog);  // 补充动态点赞状态
        return Result.ok(blog);
    }

    /**
     * 点赞或取消点赞动态
     * 根据当前登录用户是否已点赞，决定执行点赞还是取消点赞，
     * 并同步更新数据库点赞数、Redis点赞集合
     * @param id 动态 id
     * @return 操作结果
     */
    @Override
    public Result likePost(Long id) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前登录用户是否已经点赞
        String key = POST_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            //3. 未点赞
            // 3.1.数据库点赞数 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                // 3.2.保存用户到Redis的set集合
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.如果已点赞，取消点赞
            // 4.1.数据库点赞数 -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                // 4.2.把用户从Redis的set集合移除
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }
    /**
     * 补充动态作者信息
     * @param blog 动态
     * @return 动态作者信息
     */
    @Override
        public Result queryPostUser(Post blog) {
        Long userId = blog.getUserId();// 获取动态作者的id
        User user = userService.getById(userId);//动态作者个人信息
        blog.setName(user.getNickName());//补充动态作者昵称
        blog.setIcon(user.getIcon());//补充动态作者头像
        return Result.ok(blog);
    }
    /**
     * 判断动态点赞状态
     */
    private void isPostLiked(Post blog) {
        // 1.获取Post用户
        UserDTO user = UserHolder.getUser();
        if( user == null){
            return; // 用户未登录，无需判断点赞状态
        }
        Long userId = user.getId();
        // 2.判断当前登录用户是否已经点赞
        String key = POST_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);
    }

    public Result queryPostLikes(Long id) {
        String key = POST_LIKED_KEY + id;
        //获取redis top5的点赞用户
         Set<String> top5= stringRedisTemplate.opsForZSet().range(key, 0, 4);   //set集合：<指定类型>不重复集合
        if(top5 == null || top5.isEmpty())
            return Result.ok(Collections.emptyList());
        // 2.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf)// 将String转换为Long
                .collect(Collectors.toList());//等价List<Long> list = [1L, 23L,..., 12L]
        // 3.根据用户id查询用户    SELECT id FROM tb_user WHERE id IN ( ? , ? ) ORDER BY FIELD(id, 1011 , 1 )
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id, " + idStr + ")").list().stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    /**
     * 新增户外动态并推送到粉丝收件箱
     * 先保存动态到数据库，再查询作者的所有粉丝，
     * 把动态id以当前时间戳为score写入每个粉丝的收件箱（SortedSet）
     * @param blog 动态内容
     * @return 返回动态id
     */
    @Override
    public Result savePost(Post blog) {
        // 0.1. 参数校验
        if (blog.getShopId() == null) {
            return Result.fail("请先关联场馆再发布");
        }
        if (StrUtil.isBlank(blog.getContent())) {
            return Result.fail("正文部分不能为空");
        }
        if (StrUtil.isBlank(blog.getTitle())) {
            return Result.fail("标题不能为空");
        }
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2.保存户外动态
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("保存动态失败");
        }
        // 3.查询动态作者的所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> postUserfans = followService.query()
                .eq("follow_user_id", blog.getUserId()).list();
        // 4.推送动态id给所有粉丝
        for (Follow follow : postUserfans) {
            // 4.1.获取粉丝id
            Long userId = follow.getUserId();
            // 4.2.推送
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }
    /**
     * 根据用户关注的博主的动态，分页查询博主的动态
     * @param max 最新时间
     * @param offset 偏移量
     * @return 博主的动态
     */
    @Override
    public Result queryPostByFollow(Long max, Integer offset) {
        // 1. 获取当前用户
        UserDTO user = UserHolder.getUser();
        // 2. 查询收件箱
        String key = FEED_KEY + user.getId();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        // 判断收件箱是否为空
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 3. 解析数据: blogId、minTime（时间戳）、offset
        List<Long> ids = new ArrayList<>(typedTuples.size());   //提取出所有的动态id转Long存入列表，方便后续去数据库批量查询。
        long minTime = 0L;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 3.1. 获取blogId
            ids.add(Long.valueOf(tuple.getValue()));
            long time  = tuple.getScore().longValue();
            if(minTime == time)
            {
                os++;
            }else {
                minTime = time;
                os = 1;
            }
            // 3.2. 获取minTime
            minTime = tuple.getScore().longValue();
        }
        // 4. 根据动态id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Post> posts = query().in("id", ids).last("ORDER BY FIELD(id, " + idStr + ")").list();
        //4.1点赞数据
        for (Post blog : posts){
            queryPostUser(blog);
            isPostLiked(blog);
        }
        // 5. 封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(posts);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }
}
