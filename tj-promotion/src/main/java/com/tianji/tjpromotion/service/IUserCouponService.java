package com.tianji.tjpromotion.service;

import com.tianji.tjpromotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author author
 * @since 2024-09-25
 */
public interface IUserCouponService extends IService<UserCoupon> {

    void receuveCoupon(Long id);
}
