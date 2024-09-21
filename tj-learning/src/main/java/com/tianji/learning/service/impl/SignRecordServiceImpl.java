package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.Constants.RedisConstants;
import com.tianji.learning.domain.vo.SignRecordVO;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {
    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper mqHelper;
    @Override
    public SignResultVO addSignRecords() {
        // 获取用户id
        Long userId = UserContext.getUser();
        // 拼接key
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;
        // 利用bitset命令将签到记录保存到redis中，判断是否签过
        Boolean flag = redisTemplate.opsForValue().setBit(key, now.getDayOfMonth() - 1, true);
        if(flag.equals(true)) {
            // 已经签过到
            throw new BadRequestException("已经签过到");
        }
        // 计算连续天数
        int days = countSignDays(key, now.getDayOfMonth());
        // 根据天数奖励积分
        int fenshu = getFenshuByDays(days);
        // todo 保存积分
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, fenshu + 1)
        );

        // 封装vo并返回
        SignResultVO VO = new SignResultVO();
        VO.setRewardPoints(fenshu);
        VO.setSignDays(days);
        return VO;
    }

    @Override
    public Byte[] GetStatus() {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.获取日期
        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();
        // 3.拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        // 4.读取
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return new Byte[0];
        }
        int num = result.get(0).intValue();

        Byte[] arr = new Byte[dayOfMonth];
        int pos = dayOfMonth - 1;
        while (pos >= 0){
            arr[pos--] = (byte)(num & 1);
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return arr;
    }

    private int getFenshuByDays(int days) {
        int num = 0;
        if(days == 7) {
            num = 10;
        } else if(days == 14) {
            num = 10;
        } else if (days == 28) {
            num = 40;
        }
        return num;
    }

    // 计算连续签到多少天
    private int countSignDays(String key, int dayOfMonth) {
        int tot = 0;
        List<Long> longs = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if(longs == null || longs.isEmpty()) {
            return 0;
        }
        Long num = longs.get(0);// 从第一天到现在的签到数据
        log.debug("num:{}",num);
        while(num !=0) {
            Long cnt = num % 2;
            if(cnt == 0) {
                break;
            }
            tot++;
            num = num / 2;
        }
        return tot;
    }
}
