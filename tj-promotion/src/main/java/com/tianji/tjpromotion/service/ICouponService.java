package com.tianji.tjpromotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.tjpromotion.domain.dto.CouponFormDTO;
import com.tianji.tjpromotion.domain.dto.CouponIssueFormDTO;
import com.tianji.tjpromotion.domain.po.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.tjpromotion.domain.query.CouponQuery;
import com.tianji.tjpromotion.domain.vo.CouponPageVO;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author author
 * @since 2024-09-23
 */
public interface ICouponService extends IService<Coupon> {

    void saveCoupon(CouponFormDTO dto);

    PageDTO<CouponPageVO> queryCouponPage(CouponQuery query);

    void issueCoupon(CouponIssueFormDTO dto);
}
