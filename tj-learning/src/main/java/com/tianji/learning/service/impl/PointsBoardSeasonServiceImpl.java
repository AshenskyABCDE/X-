package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.learning.Constants.LearningContstants;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.sql.Wrapper;
import java.time.LocalDate;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author author
 * @since 2024-09-20
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {

    @Override
    public void createPointsBoardLatestTable(Integer id) {
        System.out.println("当前创建表是" + LearningContstants.POINTS_BOARD_TABLE_PREFIX + id);
        getBaseMapper().createPointsBoardLatestTable(LearningContstants.POINTS_BOARD_TABLE_PREFIX + id);
    }

    @Override
    public Integer querySeasonByTime(LocalDate date) {
        PointsBoardSeason one = lambdaQuery().le(PointsBoardSeason::getBeginTime, date)
                .ge(PointsBoardSeason::getEndTime, date)
                .one();
        return one.getId();
    }
}
