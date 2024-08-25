//package com.tianji.remark.service.impl;
//
//import com.tianji.api.client.user.UserClient;
//import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
//import com.tianji.common.constants.MqConstants;
//import com.tianji.common.utils.CollUtils;
//import com.tianji.common.utils.StringUtils;
//import com.tianji.common.utils.UserContext;
//import com.tianji.remark.domain.dto.LikeRecordFormDTO;
//import com.tianji.remark.domain.dto.LikedTimesDTO;
//import com.tianji.remark.domain.po.LikedRecord;
//import com.tianji.remark.mapper.LikedRecordMapper;
//import com.tianji.remark.service.ILikedRecordService;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
///**
// * <p>
// * 点赞记录表 服务实现类
// * </p>
// *
// * @author author
// * @since 2024-08-24
// */
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
//
//    private final RabbitMqHelper rabbitMqHelper;
//    @Override
//    public void addLikeRecord(LikeRecordFormDTO dto) {
//        Long userId = UserContext.getUser();
//
//        boolean flag = true;
//        if(dto.getLiked()) {
//            flag = liked(dto, userId);
//        } else {
//            flag = unliked(dto, userId);
//        }
//        if(! flag) {
//            return ;
//        }
//        Integer count = Math.toIntExact(this.lambdaQuery().eq(LikedRecord::getBizId, dto.getBizId())
//                .count());
//
//        LikedTimesDTO likedTimes = new LikedTimesDTO();
//        likedTimes.setBizId(dto.getBizId());
//        likedTimes.setLikedTimes(count);
//
//        log.info("开始发送消息");
//        rabbitMqHelper.send(
//                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
//                StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE,dto.getBizType()),
//                likedTimes
//        );
//    }
//
//    @Override
//    public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
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
//    }
//
//    private boolean unliked(LikeRecordFormDTO dto, Long userId) {
//        LikedRecord record = this.lambdaQuery().eq(LikedRecord::getUserId, UserContext.getUser())
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .one();
//        if(record == null) {
//            // 如果没有点过赞
//            return false;
//        }
//        boolean flag = this.removeById(record.getId());
//        return flag;
//    }
//
//    private boolean liked(LikeRecordFormDTO dto, Long userId) {
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
//    }
//}
