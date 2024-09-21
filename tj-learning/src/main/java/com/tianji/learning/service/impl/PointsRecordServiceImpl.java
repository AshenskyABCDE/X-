package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author author
 * @since 2024-09-20
 */
@Service
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    @Override
    public void addPointsRecord(Long userId, Integer points, PointsRecordType pointsRecordType) {
        // 判断积分是否达到上限，type里面的max
        int maxPoints = pointsRecordType.getMaxPoints();
        int realPoints = points;
        if(maxPoints > 0) {
            // 查询该用户积分类型今日已得
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
            LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);
            QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
            wrapper.select("sum(points) as totalPoints")
                    .eq("user_id", userId)
                    .eq("type", pointsRecordType)
                    .between("create_time", dayStartTime, dayEndTime);
            Map<String, Object> map = this.getMap(wrapper);
            int tot = 0;
            if(map != null) {
                BigDecimal total = (BigDecimal)map.get("totalPoints");
                tot = total.intValue();
            }
            if(tot >= maxPoints) {
                return ;
            }
            if(realPoints + tot > maxPoints) {
                realPoints = maxPoints - tot;
            }
        }
        PointsRecord record = new PointsRecord();
        record.setUserId(userId);
        record.setType(pointsRecordType);
        record.setPoints(realPoints);
        this.save(record);
    }

    @Override
    public List<PointsStatisticsVO> queryMyTodayPoints() {
        Long userId = UserContext.getUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
        LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.select("type, sum(points) as points")
                .eq("user_id", userId)
                .between("create_time", dayStartTime, dayEndTime)
                .groupBy("type");
        List<PointsRecord> list = this.list(wrapper);
        if(CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }
        List<PointsStatisticsVO> voList = new ArrayList<>();
        for(PointsRecord record : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setPoints(record.getPoints());
            vo.setMaxPoints(record.getType().getMaxPoints());
            vo.setType(record.getType().getDesc());
            voList.add(vo);
        }
        return voList;
    }

    @Override
    public void listenWriteReplyMessage(Long userId) {
        System.out.println("接受问答积分队列-------");
        int maxPoints = PointsRecordType.QA.getMaxPoints();
        int realPoints = 2;
        if(maxPoints > 0) {
            // 查询该用户积分类型今日已得
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
            LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);
            QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
            wrapper.select("sum(points) as totalPoints")
                    .eq("user_id", userId)
                    .eq("type", PointsRecordType.QA)
                    .between("create_time", dayStartTime, dayEndTime);
            Map<String, Object> map = this.getMap(wrapper);
            int tot = 0;
            if(map != null) {
                BigDecimal total = (BigDecimal)map.get("totalPoints");
                tot = total.intValue();
            }
            if(tot >= maxPoints) {
                return ;
            }
            if(realPoints + tot > maxPoints) {
                realPoints = maxPoints - tot;
            }
        }
        PointsRecord record = new PointsRecord();
        record.setUserId(userId);
        record.setType(PointsRecordType.QA);
        record.setPoints(realPoints);
        boolean save = this.save(record);
        System.out.println("保存成功了吗？" + save);
    }
}
