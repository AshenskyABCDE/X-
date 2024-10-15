package com.tianji.tjpromotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.db.sql.Order;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.tjpromotion.constants.PromotionConstants;
import com.tianji.tjpromotion.discount.Discount;
import com.tianji.tjpromotion.discount.DiscountStrategy;
import com.tianji.tjpromotion.domain.dto.CouponDiscountDTO;
import com.tianji.tjpromotion.domain.dto.OrderCourseDTO;
import com.tianji.tjpromotion.domain.dto.UserCouponDTO;
import com.tianji.tjpromotion.domain.po.Coupon;
import com.tianji.tjpromotion.domain.po.CouponScope;
import com.tianji.tjpromotion.domain.po.ExchangeCode;
import com.tianji.tjpromotion.domain.po.UserCoupon;
import com.tianji.tjpromotion.enums.ExchangeCodeStatus;
import com.tianji.tjpromotion.mapper.CouponMapper;
import com.tianji.tjpromotion.mapper.UserCouponMapper;
import com.tianji.tjpromotion.service.ICouponScopeService;
import com.tianji.tjpromotion.service.IExchangeCodeService;
import com.tianji.tjpromotion.service.IUserCouponService;
import com.tianji.tjpromotion.utils.CodeUtil;
import com.tianji.tjpromotion.utils.PermuteUtil;
import io.netty.util.concurrent.CompleteFuture;
import io.reactivex.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author author
 * @since 2024-09-25
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCouponMqServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final IExchangeCodeService exchangeCodeService;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final RabbitMqHelper mqHelper;
    private final ICouponScopeService couponScopeService;
    private final Executor calculteSolutionExecutor;

    @Override
    @Transactional
    public void receuveCoupon(Long couponId) {
//        // 1.查询优惠券
//        Coupon coupon = couponMapper.selectById(couponId);
        // 从redis中获取信息
          Coupon coupon = queryCouponByCache(couponId);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        // 2.校验发放时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券发放已经结束或尚未开始");
        }
        // 3.校验库存
        if (coupon.getTotalNum() <= 0) {
            throw new BadRequestException("优惠券库存不足");
        }

        // 统计已领取的数量
        Long userId = UserContext.getUser();
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long increment = redisTemplate.opsForHash().increment(key, userId.toString(), 1);

        if(increment > coupon.getUserLimit()) {
            throw new BizIllegalException("超出限领数量");
        }

        // 修改优惠券的库存
        String couponKey = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        redisTemplate.opsForHash().increment(couponKey, "totalNum", -1);
        UserCouponDTO msg= new UserCouponDTO();
        msg.setCouponId(couponId);
        msg.setUserId(userId);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE,
                MqConstants.Key.COUPON_RECEIVE,
                msg);
//        synchronized (UserContext.getUser().toString().intern()) {
//            IUserCouponService userCouponServicePoxy = (IUserCouponService) AopContext.currentProxy();
//            checkAndCreateUserCoupon(UserContext.getUser(),coupon,  null);
//        }

//        String key = "lock:coupon:uid:" + UserContext.getUser();
//        RLock lock = redissonClient.getLock(key);

//        try {
//            boolean isLock = lock.tryLock();
//            if(!isLock) {
//                throw new BizIllegalException("操作太频繁");
//            }
//            IUserCouponService userCouponServicePoxy = (IUserCouponService) AopContext.currentProxy();
//            userCouponServicePoxy.checkAndCreateUserCoupon(UserContext.getUser(),coupon,  null);
//        } finally {
//            lock.unlock();
//        }
    }

    // 从redis中获取优惠券信息
    private Coupon queryCouponByCache(Long couponId) {
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Coupon coupon = BeanUtils.mapToBean(entries, Coupon.class, false, CopyOptions.create());
        return coupon;
    }

    @Override
    @Transactional
    public void exchangeCoupon(String code) {
        if(StringUtils.isBlank(code)) {
            throw new BadRequestException("非法参数");
        }
        long serialNum = CodeUtil.parseCode(code);
        boolean result = exchangeCodeService.updateExchangeCodeMark(serialNum, true);
        if(result) {
            throw new BizIllegalException("已被使用");
        }
        try {
            ExchangeCode exchangeCode = exchangeCodeService.getById(serialNum);
            if(exchangeCode == null) {
                throw new BizIllegalException("兑换码不存在");
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expireTime = exchangeCode.getExpiredTime();
            if(now.isAfter(expireTime)) {
                throw new BizIllegalException("兑换码过期");
            }
            Long user = UserContext.getUser();
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            if(coupon == null) {
                throw new BizIllegalException("不存在");
            }
            checkAndCreateUserCoupon(user, coupon, serialNum);
        } catch (Exception e) {
            exchangeCodeService.updateExchangeCodeMark(serialNum, false);
        }
    }

    @Transactional
    public void checkAndCreateUserCoupon(Long user, Coupon coupon, Long serialNum) {
        // toString 是new String 是不同的对象
        // intern是从常量池中获取，因此是同一对象
            Long userId = UserContext.getUser();
            Long couponId = coupon.getId();
            Integer count = Math.toIntExact(this.lambdaQuery()
                    .eq(UserCoupon::getId, UserContext.getUser())
                    .eq(UserCoupon::getCouponId, couponId)
                    .count());
            // 4.2.校验限领数量
            if(count != null && count >= coupon.getUserLimit()){
                throw new BadRequestException("超出领取数量");
            }
            // 5.更新优惠券的已经发放的数量 + 1
            couponMapper.incrIssueNum(coupon.getId());
            // 6.新增一个用户券
            saveUserCoupon(coupon, userId);
            if(serialNum != null) {
                exchangeCodeService.lambdaUpdate().set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                        .set(ExchangeCode::getUserId, userId)
                        .eq(ExchangeCode::getId, serialNum)
                        .update();
            }
    }

    @Transactional
    @Override
    public void checkAndCreateUserCouponNew(UserCouponDTO msg) {
        // toString 是new String 是不同的对象
        // intern是从常量池中获取，因此是同一对象
//        Long userId = UserContext.getUser();
//        Long couponId = coupon.getId();
//        Integer count = Math.toIntExact(this.lambdaQuery()
//                .eq(UserCoupon::getId, UserContext.getUser())
//                .eq(UserCoupon::getCouponId, couponId)
//                .count());
//        // 4.2.校验限领数量
//        if(count != null && count >= coupon.getUserLimit()){
//            throw new BadRequestException("超出领取数量");
//        }
//        // 5.更新优惠券的已经发放的数量 + 1
//        couponMapper.incrIssueNum(coupon.getId());
//        // 6.新增一个用户券
//        saveUserCoupon(coupon, userId);
        Coupon coupon = couponMapper.selectById(msg.getCouponId());
        if(coupon == null) {
            return;
        }
        int num = couponMapper.incrIssueNum(coupon.getId());
        if(num == 0) {
            return ;
        }
        saveUserCoupon(coupon, msg.getUserId());
    }

    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses) {
        // 查询当前用户可用的优惠券 coupon 和 usercoupon
        List<Coupon> coupons = getBaseMapper().queryMyCoupon(UserContext.getUser());
        if(CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        // 初筛
        int sum = 0;
        for(OrderCourseDTO c: courses) {
            sum += c.getPrice();
        }

        // 检验优惠券是否可用
        List<Coupon> avaliavleCoupons = new ArrayList<>();
        for (Coupon coupon: coupons) {
            boolean b = DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(sum, coupon);
            if(b) {
                avaliavleCoupons.add(coupon);
            }
        }
        if(CollUtils.isEmpty(avaliavleCoupons)) {
            return CollUtils.emptyList();
        }
        // 细筛
        Map<Coupon, List<OrderCourseDTO>> avaMap = findAvaliableCoupons(avaliavleCoupons, courses);
        if(avaMap.isEmpty()) {
            return CollUtils.emptyList();
        }
        Set<Coupon> couponSet = avaMap.keySet();
        avaliavleCoupons = new ArrayList<>(couponSet);
        // 将优惠券排列组合 选出最少的
        List<List<Coupon>> solutions = PermuteUtil.permute(avaliavleCoupons);
        // 还可以使用单劵
        for(Coupon avaliableCoupon : avaliavleCoupons) {
            solutions.add(List.of(avaliableCoupon));
        }
//        // 开始组合方案
//        List<CouponDiscountDTO> dtos = new ArrayList<>();
//        for(List<Coupon> solution : solutions) {
//            CouponDiscountDTO dto = calculateSolutionDiscount(avaMap, courses, solution);
//            log.debug("方案最终优惠:{} 方案中id有:{} 规则:{}", dto.getDiscountAmount(), dto.getIds(), dto.getRules());
//            dtos.add(dto);
//        }
        // 多线程改造
//        List<CouponDiscountDTO> dtos = new ArrayList<>();
        // 线程安全的集合
        List<CouponDiscountDTO> dtos = Collections.synchronizedList(new ArrayList<>(solutions.size()));
        CountDownLatch latch = new CountDownLatch(solutions.size());
        for(List<Coupon> solution : solutions) {
            CompletableFuture.supplyAsync(new Supplier<CouponDiscountDTO>() {
                @Override
                public CouponDiscountDTO get() {
                    CouponDiscountDTO dto = calculateSolutionDiscount(avaMap, courses, solution);
                    return dto;
                }
            }, calculteSolutionExecutor).thenAccept(new Consumer<CouponDiscountDTO>() {
                @Override
                public void accept(CouponDiscountDTO dto) {
                    log.debug("方案最终优惠:{} 方案中id有:{} 规则:{}", dto.getDiscountAmount(), dto.getIds(), dto.getRules());
                    dtos.add(dto);
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("多线程计算组合优惠明细" , e);
        }
        
        // 筛选最优解
        return findBestSolution(dtos);
    }

    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> solutions) {
        //  创建map 记录用券相同，金额最高 金额相同，用券最少
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();
        Map<Integer, CouponDiscountDTO> lessCouponMap = new HashMap<>();
        // 循环方案
        for(CouponDiscountDTO solution: solutions) {
            String ids = solution.getIds().stream().sorted(Comparator.comparing(Long::longValue)).map(String::valueOf).collect(Collectors.joining(","));
            CouponDiscountDTO old = moreDiscountMap.get(ids);
            if(old != null && old.getDiscountAmount() >= solution.getDiscountAmount()) {
                continue;
            }
            old = lessCouponMap.get(solution.getDiscountAmount());
            int now = solution.getIds().size();
            if(old != null && now > 1 && old.getIds().size() <= now) {
                continue;
            }
            // 更新方案到map
            moreDiscountMap.put(ids, solution);
            lessCouponMap.put(solution.getDiscountAmount(), solution);
        }
        Collection<CouponDiscountDTO> intersection = CollUtils.intersection(moreDiscountMap.values(), lessCouponMap.values());
        List<CouponDiscountDTO> dtos = intersection.stream()
                .sorted(Comparator.comparing(CouponDiscountDTO::getDiscountAmount).reversed()).collect(Collectors.toList());
        return dtos;
    }

    private CouponDiscountDTO calculateSolutionDiscount(Map<Coupon, List<OrderCourseDTO>> avaMap,
                                                        List<OrderCourseDTO> courses,
                                                        List<Coupon> solution) {
        //1.创建方案dto对象
        CouponDiscountDTO dto = new CouponDiscountDTO();
        //2.初始化商品Id和折扣明细的映射
        Map<Long, Integer> detailMap = courses.stream().collect(Collectors.toMap(OrderCourseDTO::getId, orderCourseDTO -> 0));
        // 循环方案的优惠券
        for(Coupon coupon : solution) {
            // 取出优惠券对应课程
            List<OrderCourseDTO> avaliableCourses = avaMap.get(coupon);
            int sum = 0;
            for(OrderCourseDTO courseDTO : avaliableCourses) {
                sum += courseDTO.getPrice() - detailMap.get(courseDTO.getId());
            }
            // 判断优惠券是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if(!discount.canUse(sum, coupon)) {
                continue;
            }
            // 计算折扣金额
            int discountAmount = discount.calculateDiscount(sum, coupon);
            // 更新商品的折扣明细
            calculateDatailDiscount(detailMap, avaliableCourses, sum, discountAmount);
            // 累加每一个优惠券的优惠金额
            dto.getIds().add(coupon.getId());
            dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(discountAmount + dto.getDiscountAmount());
        }
        return dto;
    }

    /**
     * 计算商品 折扣明细
     * @param detailMap 商品id 和 该 商品的明细 的映射
     * @param avaliableCourses 当前优惠券可用的课程集合
     * @param sum 可用的课程的总金额
     * @param discountAmount 当前优惠券能优惠的金额
     */
    private void calculateDatailDiscount(Map<Long, Integer> detailMap, List<OrderCourseDTO> avaliableCourses, int sum, int discountAmount) {
        int remainedDiscount = discountAmount;
        // 前面的商品按比例计算，最后一个商品折扣明细 总 - 前面优惠的金额
        int time = 0;
        for (OrderCourseDTO avaliableCourse : avaliableCourses) {
            time++;
            int price = 0;
            if(time == avaliableCourses.size()) {
                price = remainedDiscount;
            } else {
                price = avaliableCourse.getPrice() * discountAmount / sum ;
                remainedDiscount = remainedDiscount - price;
            }
            // 将商品的明细添加到map
            detailMap.put(avaliableCourse.getId(), detailMap.getOrDefault(avaliableCourse.getId(),0) + price);
        }
    }

    private Map<Coupon, List<OrderCourseDTO>> findAvaliableCoupons(List<Coupon> coupons, List<OrderCourseDTO> orderCourses) {
        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<Coupon, List<OrderCourseDTO>>();
        // 1.循环遍历初筛后的

        for(Coupon coupon : coupons) {
            // 判断优惠券是否限定了范围
            List<OrderCourseDTO> avaliableCourse = orderCourses;
            if(coupon.getSpecific()) {
                List<CouponScope> list = couponScopeService.lambdaQuery().eq(CouponScope::getCouponId, coupon.getId()).list();
                List<Long> scope = list.stream().map(CouponScope::getBizId).collect(Collectors.toList());
                // 筛选在范围内的课程
                avaliableCourse = orderCourses.stream().filter(new Predicate<OrderCourseDTO>() {
                    @Override
                    public boolean test(OrderCourseDTO orderCourseDTO) {
                        return scope.contains(orderCourseDTO.getCateId());
                    }
                }).collect(Collectors.toList());
            }
            if(CollUtils.isEmpty(avaliableCourse)) {
                continue;
            }
            // 计算可用课程的价格
            int sum = 0;
            for(OrderCourseDTO dto : avaliableCourse) {
                sum += dto.getPrice();
            }
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if(discount.canUse(sum, coupon)) {
                map.put(coupon, avaliableCourse);
            }
        }
        // 2.找出优惠券的可用可成
        // 3.计算优惠券
        // 4.判断该优惠券是否可用
        return map;
    }

    private void saveUserCoupon(Coupon coupon, Long userId) {
        // 1.基本信息
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        // 2.有效期信息
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        if (termBeginTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        uc.setTermBeginTime(termBeginTime);
        uc.setTermEndTime(termEndTime);
        // 3.保存
        save(uc);
    }

}
