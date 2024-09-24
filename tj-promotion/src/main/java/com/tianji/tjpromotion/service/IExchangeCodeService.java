package com.tianji.tjpromotion.service;

import com.tianji.tjpromotion.domain.po.Coupon;
import com.tianji.tjpromotion.domain.po.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author author
 * @since 2024-09-23
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateExchangeCode(Coupon coupon);
}
