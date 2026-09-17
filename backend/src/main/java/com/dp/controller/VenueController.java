package com.dp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dp.dto.Result;
import com.dp.entity.Venue;
import com.dp.service.IVenueService;
import com.dp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/shop")
public class VenueController {

    @Resource
    public IVenueService venueService;

    /**
     * 根据id查询场馆信息
     * @param id 场馆id
     * @return 场馆详情数据
     */
    @GetMapping("/{id}")
    public Result queryVenueById(@PathVariable("id") Long id) throws InterruptedException {
        return venueService.queryById(id);
    }
    /**
     * 新增场馆信息
     * @param shop 场馆数据
     * @return 场馆id
     */
    @PostMapping
    public Result saveVenue(@RequestBody Venue shop) {
        // 写入数据库
        venueService.save(shop);
        // 返回场馆id
        return Result.ok(shop.getId());
    }

    /**
     * 更新场馆信息
     * @param shop 场馆数据
     * @return 无
     */
    @PutMapping
    public Result updateVenue(@RequestBody Venue shop) {
        // 写入数据库
        return venueService.update(shop);
    }

    /**
     * 根据场馆类型分页查询场馆信息
     * @param typeId 场馆类型
     * @param current 页码
     * @return 场馆列表
     */
    @GetMapping("/of/type")
    public Result queryVenueByType(@RequestParam("typeId") Integer typeId,
                                  @RequestParam("current") Integer current,
                                  @RequestParam("x") Double x,
                                  @RequestParam("y") Double y) {
        return venueService.queryVenueByType(typeId, current, x, y);
    }
    /**
     * 根据场馆名称关键字分页查询场馆信息
     * @param name 场馆名称关键字
     * @param current 页码
     * @return 场馆列表
     */
    @GetMapping("/of/name")
    public Result queryVenueByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Venue> page = venueService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
