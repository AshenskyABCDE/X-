package com.tianji.learning.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.Constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author author
 * @since 2024-09-20
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;

    private final UserClient userClient;
    @Override
    public PointsBoardVO queryPointsBoard(PointsBoardQuery query) {
        PointsBoardVO vo = new PointsBoardVO();
        // 获取当前用户ID
        Long userId = UserContext.getUser();
        // 判断是当前赛季还是历史赛季 如果是null或0就代表当前赛季 在redis中获取，历史赛季用mysql
        Boolean season = query.getSeason() == null || query.getSeason() == 0;

        String format = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;
        // 查询我的排名和积分
        PointsBoard board = null;
        if(season) {
            board = queryMYCurrentBoard(key);
        } else {
            board = queryMyHistoryBoard(query.getSeason());
        }
        // 查询赛季排名
        List<PointsBoard> list = new ArrayList<>();
        if(season) {
            list = queryCurrentBoard(key , query.getPageNo(), query.getPageSize());
        }

        Set<Long> collect = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> userDTOS = userClient.queryUserByIds(collect);
        if(CollUtils.isEmpty(userDTOS)) {
            throw new BizIllegalException("用户不存在");
        }
        Map<Long, String> map = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c.getName()));
        // 分装结果
        vo.setPoints(board.getPoints());
        vo.setRank(board.getRank());

        List<PointsBoardItemVO> voList = new ArrayList<>();
        for(PointsBoard pointsBoard : list) {
            PointsBoardItemVO itemVO = new PointsBoardItemVO();
            itemVO.setName(map.get(pointsBoard.getUserId()));
            itemVO.setPoints(pointsBoard.getPoints());
            itemVO.setRank(pointsBoard.getRank());
            voList.add(itemVO);
        }
        vo.setBoardList(voList);
        return vo;
    }

    private List<PointsBoard> queryCurrentBoard(String key, Integer pageNo, Integer pageSize) {
        int from = (pageNo - 1) * pageSize;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, from, from + pageSize - 1);
        if (CollUtils.isEmpty(typedTuples)) {
            return CollUtils.emptyList();
        }
        // 3.封装
        int rank = from + 1;
        List<PointsBoard> list = new ArrayList<>(typedTuples.size());
        for(ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            String userId = tuple.getValue();
            Double points = tuple.getScore();
            if(userId == null || points == null) {
                continue;
            }
            PointsBoard board = new PointsBoard();
            board.setUserId(Long.valueOf(userId));
            board.setPoints(points.intValue());
            board.setRank(rank++);
            list.add(board);
        }
        return list;
    }

    private PointsBoard queryMyHistoryBoard(Long season) {
        return null;
    }

    private PointsBoard queryMYCurrentBoard(String key) {
        PointsBoard board = new PointsBoard();
        Long userId = UserContext.getUser();
        board.setUserId(userId);
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        Long rank = redisTemplate.opsForZSet().rank(key, userId.toString());
        board.setPoints(score == null ? 0 : score.intValue());
        board.setRank(rank == null ? 0 : (int) (rank + 1));
        return board;
    }
}
