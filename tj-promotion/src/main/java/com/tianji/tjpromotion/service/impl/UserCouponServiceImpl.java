//package com.tianji.tjpromotion.service.impl;
//
//import com.tianji.common.exceptions.BadRequestException;
//import com.tianji.common.exceptions.BizIllegalException;
//import com.tianji.common.utils.StringUtils;
//import com.tianji.common.utils.UserContext;
//import com.tianji.tjpromotion.domain.po.Coupon;
//import com.tianji.tjpromotion.domain.po.ExchangeCode;
//import com.tianji.tjpromotion.domain.po.UserCoupon;
//import com.tianji.tjpromotion.enums.ExchangeCodeStatus;
//import com.tianji.tjpromotion.mapper.CouponMapper;
//import com.tianji.tjpromotion.mapper.UserCouponMapper;
//import com.tianji.tjpromotion.service.IExchangeCodeService;
//import com.tianji.tjpromotion.service.IUserCouponService;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import com.tianji.tjpromotion.utils.CodeUtil;
//import lombok.RequiredArgsConstructor;
//import org.springframework.aop.framework.AopContext;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//
///**
// * <p>
// * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
// * </p>
// *
// * @author author
// * @since 2024-09-25
// */
//@Service
//@RequiredArgsConstructor
//public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {
//
//    private final CouponMapper couponMapper;
//    private final IExchangeCodeService exchangeCodeService;
//
//    @Override
//    @Transactional
//    public void receuveCoupon(Long couponId) {
//        // 1.查询优惠券
//        Coupon coupon = couponMapper.selectById(couponId);
//        if (coupon == null) {
//            throw new BadRequestException("优惠券不存在");
//        }
//        // 2.校验发放时间
//        LocalDateTime now = LocalDateTime.now();
//        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
//            throw new BadRequestException("优惠券发放已经结束或尚未开始");
//        }
//        // 3.校验库存
//        if (coupon.getIssueNum() >= coupon.getTotalNum()) {
//            throw new BadRequestException("优惠券库存不足");
//        }
//        synchronized (UserContext.getUser().toString().intern()) {  
//            IUserCouponService userCouponServicePoxy = (IUserCouponService) AopContext.currentProxy();
//            checkAndCreateUserCoupon(UserContext.getUser(),coupon,  null);
//        }
//    }
//
//    @Override
//    @Transactional
//    public void exchangeCoupon(String code) {
//        if(StringUtils.isBlank(code)) {
//            throw new BadRequestException("非法参数");
//        }
//        long serialNum = CodeUtil.parseCode(code);
//        boolean result = exchangeCodeService.updateExchangeCodeMark(serialNum, true);
//        if(result) {
//            throw new BizIllegalException("已被使用");
//        }
//        try {
//            ExchangeCode exchangeCode = exchangeCodeService.getById(serialNum);
//            if(exchangeCode == null) {
//                throw new BizIllegalException("兑换码不存在");
//            }
//            LocalDateTime now = LocalDateTime.now();
//            LocalDateTime expireTime = exchangeCode.getExpiredTime();
//            if(now.isAfter(expireTime)) {
//                throw new BizIllegalException("兑换码过期");
//            }
//            Long user = UserContext.getUser();
//            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
//            if(coupon == null) {
//                throw new BizIllegalException("不存在");
//            }
//            checkAndCreateUserCoupon(user, coupon, serialNum);
//        } catch (Exception e) {
//            exchangeCodeService.updateExchangeCodeMark(serialNum, false);
//        }
//    }
//
//    @Transactional
//    public void checkAndCreateUserCoupon(Long user, Coupon coupon, Long serialNum) {
//        // toString 是new String 是不同的对象
//        // intern是从常量池中获取，因此是同一对象
//            Long userId = UserContext.getUser();
//            Long couponId = coupon.getId();
//            Integer count = Math.toIntExact(this.lambdaQuery()
//                    .eq(UserCoupon::getId, UserContext.getUser())
//                    .eq(UserCoupon::getCouponId, couponId)
//                    .count());
//            // 4.2.校验限领数量
//            if(count != null && count >= coupon.getUserLimit()){
//                throw new BadRequestException("超出领取数量");
//            }
//            // 5.更新优惠券的已经发放的数量 + 1
//            couponMapper.incrIssueNum(coupon.getId());
//            // 6.新增一个用户券
//            saveUserCoupon(coupon, userId);
//            if(serialNum != null) {
//                exchangeCodeService.lambdaUpdate().set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
//                        .set(ExchangeCode::getUserId, userId)
//                        .eq(ExchangeCode::getId, serialNum)
//                        .update();
//            }
//    }
//
//    private void saveUserCoupon(Coupon coupon, Long userId) {
//        // 1.基本信息
//        UserCoupon uc = new UserCoupon();
//        uc.setUserId(userId);
//        uc.setCouponId(coupon.getId());
//        // 2.有效期信息
//        LocalDateTime termBeginTime = coupon.getTermBeginTime();
//        LocalDateTime termEndTime = coupon.getTermEndTime();
//        if (termBeginTime == null) {
//            termBeginTime = LocalDateTime.now();
//            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
//        }
//        uc.setTermBeginTime(termBeginTime);
//        uc.setTermEndTime(termEndTime);
//        // 3.保存
//        save(uc);
//    }
//
//}
