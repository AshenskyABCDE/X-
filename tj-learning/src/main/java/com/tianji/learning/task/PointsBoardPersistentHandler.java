package com.tianji.learning.task;

import com.tianji.common.utils.CollUtils;
import com.tianji.learning.Constants.LearningContstants;
import com.tianji.learning.Constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService boardSeasonService;

    private final IPointsBoardSeasonService seasonService;

    private final IPointsBoardService boardService;

    private final StringRedisTemplate redisTemplate;

    //@Scheduled(cron = "0 0 3 1 * ?")
    //@Scheduled(cron = "0 25 11 22 9 ?")
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeanson() {
        LocalDate time = LocalDate.now().minusMonths(1);
        PointsBoardSeason one = boardSeasonService.lambdaQuery().le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        if(one == null) {
            return ;
        }
        boardSeasonService.createPointsBoardLatestTable(one.getId());
    }

    @XxlJob("savePointsBoard2DB")
    @Transactional
    public void savePointsBoard2DB() {
        System.out.println("开始了");
        // 获取上个月的时间
        LocalDate date = LocalDate.now().minusMonths(1);
        // 获取上个月的赛季
        Integer seanson = seasonService.querySeasonByTime(date);
        String key = LearningContstants.POINTS_BOARD_TABLE_PREFIX + seanson;
        TableInfoContext.setInfo(key);
        String str = RedisConstants.POINTS_BOARD_KEY_PREFIX + date.format(DateTimeFormatter.ofPattern("yyyyMM"));

        System.out.println("表名是" + key + "redis的名字是" + str);

        System.out.println("这个时候TableInfoContext是否存表明 " + TableInfoContext.getInfo());
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        int pageNo = shardIndex + 1;
        int pageSize = 10;
        while(true) {
            log.info("开始分区");
            List<PointsBoard> pointsBoards = boardService.queryCurrentBoard(str, pageNo, pageSize);
            if(CollUtils.isEmpty(pointsBoards)) {
                break;
            }
            for(PointsBoard board : pointsBoards) {
                // 这里因为新表只有三个属性，因此要把多余的rank属性清除
                board.setId(board.getRank().longValue());
                board.setRank(null);
            }
            boardService.saveBatch(pointsBoards);
            pageNo+=shardTotal;
        }
        TableInfoContext.remove();
    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis() {
        LocalDateTime dateTime = LocalDateTime.now().minusMonths(1);
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + dateTime.format(DateTimeFormatter.ofPattern("yyyyMM"));
        redisTemplate.unlink(key);
    }


}
