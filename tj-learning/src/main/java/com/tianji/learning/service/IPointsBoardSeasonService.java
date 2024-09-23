package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author author
 * @since 2024-09-20
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {

    // 创建上赛季表
    void createPointsBoardLatestTable(Integer id);

    Integer querySeasonByTime(LocalDate date);
}
