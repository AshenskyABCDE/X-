package com.tianji.tjpromotion.mapper;

import com.tianji.tjpromotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2024-09-25
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {
}
