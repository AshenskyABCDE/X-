package com.tianji.tjpromotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.tjpromotion.domain.dto.CouponFormDTO;
import com.tianji.tjpromotion.domain.dto.CouponIssueFormDTO;
import com.tianji.tjpromotion.domain.query.CouponQuery;
import com.tianji.tjpromotion.domain.vo.CouponPageVO;
import com.tianji.tjpromotion.service.ICouponScopeService;
import com.tianji.tjpromotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author author
 * @since 2024-09-23
 */
@RestController
@RequestMapping("/coupons")
@Slf4j
@Api(tags = "优惠券相关接口")
@RequiredArgsConstructor
public class CouponController {
    private final ICouponService couponService;
    @ApiOperation("增加优惠券")
    @PostMapping
    public void saveCoupon(@RequestBody @Valid CouponFormDTO dto) {
        couponService.saveCoupon(dto);
    }

    @ApiOperation("分页查询优惠券列表-管理端")
    @GetMapping("page")
    public PageDTO<CouponPageVO> queryCouponPage(CouponQuery query) {
        return couponService.queryCouponPage(query);
    }

    @ApiOperation("发放优惠券")
    @PutMapping("{id}/issue")
    public void issueCoupon(@RequestBody @Valid CouponIssueFormDTO dto) {
        couponService.issueCoupon(dto);
    }
}
