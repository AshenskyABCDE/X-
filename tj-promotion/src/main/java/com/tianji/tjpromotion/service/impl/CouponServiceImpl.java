package com.tianji.tjpromotion.service.impl;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.tjpromotion.domain.dto.CouponFormDTO;
import com.tianji.tjpromotion.domain.po.Coupon;
import com.tianji.tjpromotion.domain.po.CouponScope;
import com.tianji.tjpromotion.enums.CouponStatus;
import com.tianji.tjpromotion.mapper.CouponMapper;
import com.tianji.tjpromotion.service.ICouponScopeService;
import com.tianji.tjpromotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author author
 * @since 2024-09-23
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService scopeService;
    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO dto) {
        // dto转po
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        save(coupon);
        // 判断是否限定了范围
        if(!dto.getSpecific()) {
            return;
        }
        // 如果为true 需校验scopes值是否为空
        List<Long> scopes = dto.getScopes();
        if(CollUtils.isEmpty(scopes)) {
            throw  new BadRequestException("限定范围不能为空");
        }
        List<CouponScope> list = new ArrayList<>();
        for(Long scope : scopes) {
            CouponScope couponScope = new CouponScope();
            couponScope.setBizId(scope);
            couponScope.setCouponId(coupon.getId());
            couponScope.setType(1);
            list.add(couponScope);
        }
        // 保存优惠券的限定范围
        scopeService.saveBatch(list);
    }
}
