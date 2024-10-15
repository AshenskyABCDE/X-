package com.tianji.tjpromotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.tjpromotion.constants.PromotionConstants;
import com.tianji.tjpromotion.domain.dto.CouponFormDTO;
import com.tianji.tjpromotion.domain.dto.CouponIssueFormDTO;
import com.tianji.tjpromotion.domain.po.Coupon;
import com.tianji.tjpromotion.domain.po.CouponScope;
import com.tianji.tjpromotion.domain.po.UserCoupon;
import com.tianji.tjpromotion.domain.query.CouponQuery;
import com.tianji.tjpromotion.domain.vo.CouponPageVO;
import com.tianji.tjpromotion.domain.vo.CouponVO;
import com.tianji.tjpromotion.enums.CouponStatus;
import com.tianji.tjpromotion.enums.ObtainType;
import com.tianji.tjpromotion.enums.UserCouponStatus;
import com.tianji.tjpromotion.mapper.CouponMapper;
import com.tianji.tjpromotion.service.ICouponScopeService;
import com.tianji.tjpromotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.tjpromotion.service.IExchangeCodeService;
import com.tianji.tjpromotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.misc.Hash;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

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
@Slf4j
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService scopeService;

    private final IExchangeCodeService exchangeCodeService;

    private final IUserCouponService userCouponService;

    private final StringRedisTemplate redisTemplate;
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

    @Override
    public PageDTO<CouponPageVO> queryCouponPage(CouponQuery query) {
        Page<Coupon> page = lambdaQuery().eq(query.getType() != null, Coupon::getDiscountType, query.getType())
                .eq(query.getStatus() != null, Coupon::getStatus, query.getStatus())
                .like(StringUtils.isNotBlank(query.getName()), Coupon::getName, query.getName())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();
        if(CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<CouponPageVO> couponPageVOS = BeanUtils.copyList(records, CouponPageVO.class);
        return PageDTO.of(page, couponPageVOS);
    }

    @Override
    public void issueCoupon(CouponIssueFormDTO dto) {
        Long id = dto.getId();
        Coupon coupon = this.getById(id);
        if(coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        if(coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.PAUSE){
            throw new BizIllegalException("优惠券状态错误！");
        }
        LocalDateTime now = LocalDateTime.now();

        log.info("{},{}",dto.getIssueBeginTime(), dto.getIssueBeginTime());
        boolean b = dto.getIssueBeginTime() == null || dto.getIssueBeginTime().isAfter(now);
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);
        if(b) {
            c.setStatus(CouponStatus.ISSUING);
            c.setIssueBeginTime(now);
        } else {
            c.setStatus(CouponStatus.UN_ISSUE);
        }
        this.updateById(c);

        // 如果优惠券的信息是可发放，则应该写入redis中
        if(b) {
            log.info("{},{}",c.getUserLimit(),c.getIssueNum());
            String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id; // 优惠券Id
            redisTemplate.opsForHash().put(key, "issueBeginTime", String.valueOf(DateUtils.toEpochMilli(now)));
            redisTemplate.opsForHash().put(key, "issueEndTime", String.valueOf(DateUtils.toEpochMilli(dto.getIssueEndTime())));
            redisTemplate.opsForHash().put(key, "totalNum", String.valueOf(coupon.getTotalNum()));
            redisTemplate.opsForHash().put(key, "userLimit", String.valueOf(coupon.getUserLimit()));
        }
        if(coupon.getObtainWay().equals(ObtainType.ISSUE) && coupon.getStatus() == CouponStatus.DRAFT) {
            coupon.setIssueEndTime(c.getIssueEndTime());
            exchangeCodeService.asyncGenerateExchangeCode(coupon);
        }
    }

    @Override
    public List<CouponVO> queryIssuingCoupons() {
        List<Coupon> list = this.lambdaQuery().eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, 1)
                .list();
        if(CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }
        // 先统计当前每个用户的查询情况,不重复，用stream流转map再转list
        List<Long> users = list.stream().map(Coupon::getId).collect(Collectors.toList());
        // 每个用户优惠券领取情况,上述是获得用户，下面是通过用户来找信息
        List<UserCoupon> userCoupons = userCouponService.lambdaQuery().eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, users).list();

        // 每个用户领取了多少张
        HashMap<Long, Long> userCountMap = new HashMap<>();
        for( UserCoupon userCoupon : userCoupons) {
            userCountMap.put(userCoupon.getUserId(), userCountMap.getOrDefault(userCoupon.getUserId(), 0L) + 1);
        }
        // 每个用户领取了但没使用的数量
        HashMap<Long , Long> userUserButNotUseMap = new HashMap<>();
        for(UserCoupon userCoupon : userCoupons) {
            if(userCoupon.getStatus() == UserCouponStatus.UNUSED) {
                userUserButNotUseMap.put(userCoupon.getUserId(), userUserButNotUseMap.getOrDefault(userCoupon.getUserId(), 0L) + 1);
            }
        }

        List<CouponVO> voList = new ArrayList<>();
        for(Coupon coupon : list) {
            CouponVO couponVO = BeanUtils.copyBean(coupon, CouponVO.class);
            // 领取数量 < 优惠券数量 当前用户领取的数量 < 每人限领的数量
            couponVO.setAvailable(coupon.getIssueNum() < coupon.getTotalNum() && userCountMap.getOrDefault(coupon.getId(), 0L) < coupon.getUserLimit());
            // 用户存在领取未使用的优惠券
            couponVO.setReceived(userUserButNotUseMap.getOrDefault(coupon.getId(), 0L) > 0);
            voList.add(couponVO);
        }
        return voList;
    }
}
