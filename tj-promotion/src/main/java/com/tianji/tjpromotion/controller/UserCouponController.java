package com.tianji.tjpromotion.controller;


import com.tianji.tjpromotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.websocket.server.PathParam;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author author
 * @since 2024-09-25
 */
@RestController
@RequestMapping("/user-coupons")
@Api(tags = "用户相关接口")
@RequiredArgsConstructor
public class UserCouponController {

    private final IUserCouponService userCouponService;
    @ApiOperation("领取优惠券")
    @PostMapping("{id}/receive")
    public void receuveCoupon(@PathVariable Long id) {
        userCouponService.receuveCoupon(id);
    }
}
