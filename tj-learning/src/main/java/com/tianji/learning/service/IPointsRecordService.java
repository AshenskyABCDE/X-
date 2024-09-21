package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务类
 * </p>
 *
 * @author author
 * @since 2024-09-20
 */
public interface IPointsRecordService extends IService<PointsRecord> {

    void addPointsRecord(Long userId, Integer points, PointsRecordType pointsRecordType);

    List<PointsStatisticsVO> queryMyTodayPoints();

    void listenWriteReplyMessage(Long userId);
}
