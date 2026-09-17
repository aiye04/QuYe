package com.dp.controller;


import com.dp.dto.Result;
import com.dp.entity.VenueType;
import com.dp.service.IVenueTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class VenueTypeController {
    @Resource
    private IVenueTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        List<VenueType> typeList = typeService
                .queryByType();
        return Result.ok(typeList);
    }
}
