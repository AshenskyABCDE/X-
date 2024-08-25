package com.tianji.remark.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.dto.LikedTimesDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2024-08-24
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LikedRecordRedisServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;

    private final StringRedisTemplate redisTemplate;
    @Override
    public void addLikeRecord(LikeRecordFormDTO dto) {
        Long userId = UserContext.getUser();

        boolean flag = true;
        if(dto.getLiked()) {
            flag = liked(dto, userId);
        } else {
            flag = unliked(dto, userId);
        }
        if(! flag) {
            return ;
        }
//        Integer count = Math.toIntExact(this.lambdaQuery().eq(LikedRecord::getBizId, dto.getBizId())
//                .count());
        Long count = redisTemplate.opsForSet().size(RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId());
//        likedTimes.setBizId(dto.getBizId());
//        likedTimes.setLikedTimes(count);
//
//        log.info("开始发送消息");
//        rabbitMqHelper.send(
//                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
//                StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE,dto.getBizType()),
//                likedTimes
//        );
        if(count == null) {
            return ;
        }
        redisTemplate.opsForZSet().add(
                RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizType(),
                dto.getBizId().toString(),
                count
        );
    }

    @Override
    public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
//        if(CollUtils.isEmpty(bizIds)) {
//            return CollUtils.emptySet();
//        }
//        Long userId = UserContext.getUser();
//        List<LikedRecord> status = new ArrayList<>();
//        for(Long ids : bizIds) {
//            LikedRecord record = lambdaQuery().eq(LikedRecord::getUserId, userId)
//                    .eq(LikedRecord::getBizId, ids)
//                    .one();
//            status.add(record);
//        }
//        return list().stream().map(LikedRecord::getBizId).collect(Collectors.toSet());

            // 获取登录用户Id
        Long userId = UserContext.getUser();
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });
        return IntStream.range(0,objects.size())
                .filter(i -> (boolean) objects.get(i))
                .mapToObj(bizIds::get)
                .collect(Collectors.toSet());
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        // 读取并移除redis中缓存的点赞总数
        String s = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().popMin(s, maxBizSize);
        List<LikedTimesDTO> list = new ArrayList<>(typedTuples.size());
        for(ZSetOperations.TypedTuple<String> typedTuple: typedTuples) {
            String bizId = typedTuple.getValue();
            Double likedTimes = typedTuple.getScore();
            if(StringUtils.isBlank(bizId) || likedTimes == null) {
                continue;
            }
            LikedTimesDTO likedTimesDTO = new LikedTimesDTO();
            likedTimesDTO.setBizId(Long.valueOf(bizId));
            likedTimesDTO.setLikedTimes(likedTimes.intValue());
            list.add(likedTimesDTO);
        }
        rabbitMqHelper.send(
                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType),
                list
        );
    }


    private boolean unliked(LikeRecordFormDTO dto, Long userId) {
//        LikedRecord record = this.lambdaQuery().eq(LikedRecord::getUserId, UserContext.getUser())
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .one();
//        if(record == null) {
//            // 如果没有点过赞
//            return false;
//        }
//        boolean flag = this.removeById(record.getId());
//        return flag;
        String s = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long remove = redisTemplate.opsForSet().remove(s, userId.toString());
        return remove != null && remove > 0;
    }

    private boolean liked(LikeRecordFormDTO dto, Long userId) {
//        LikedRecord record = this.lambdaQuery().eq(LikedRecord::getUserId, UserContext.getUser())
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .one();
//        if(record != null) {
//            // 如果点过赞
//            return false;
//        }
//        // Baocunjilu
//        LikedRecord likedRecord = new LikedRecord();
//        likedRecord.setUserId(userId);
//        likedRecord.setBizId(dto.getBizId());
//        likedRecord.setBizType(dto.getBizType());
//        boolean flag = this.save(likedRecord);
//        return flag;
        String s = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long add = redisTemplate.opsForSet().add(s, userId.toString());
        return add != null && add > 0;
    }
}
