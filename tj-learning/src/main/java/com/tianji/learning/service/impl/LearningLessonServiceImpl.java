package com.tianji.learning.service.impl;

import com.alibaba.csp.sentinel.util.AssertUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author author
 * @since 2024-08-16
 */
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {


    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper recordMapper;
    @Override
    @Transactional
    public void addUserLessons(OrderBasicDTO dto) {
        //通过feign远程调用课程服务，拿到课程服务
        List<CourseSimpleInfoDTO> list = courseClient.getSimpleInfoList(dto.getCourseIds());

        // 封装po实体类
        List<LearningLesson> lists = new ArrayList<>();
        for(CourseSimpleInfoDTO cinfo : list) {
            LearningLesson lesson = new LearningLesson();
            lesson.setUserId(dto.getUserId());
            lesson.setCourseId(cinfo.getId());
            Integer validDuration = cinfo.getValidDuration(); // 单位是月
            if(validDuration != null) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                now.plusMonths(validDuration);
                lesson.setExpireTime(now);
            }
            lists.add(lesson);
        }
        this.saveBatch(lists);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        // 获取当前登录人的Id
        Long userId = UserContext.getUser();

        // 进行分页

        Page<LearningLesson> page = lambdaQuery()
                        .eq(LearningLesson::getUserId,userId)
                .page(query.toMpPage("latest_learn_time",false));
        List<LearningLesson> records = page.getRecords();
        for(LearningLesson record: records) {
            System.out.println(record);
        }
        if(records == null || records.size() == 0) {
            return PageDTO.empty(page);
        }

        // 远程调用课程服务去拿
        Set<Long> collect = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> list = courseClient.getSimpleInfoList(collect);
        if(list == null || list.size() == 0) {
            throw new BizIllegalException("课程不存在");
        }


        // 将CourseSimpleInfoDTO转换为map
        Map<Long, CourseSimpleInfoDTO> infoDtoMap = list.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        // 封装VO
        List<LearningLessonVO> voList = new ArrayList<>();
        for(LearningLesson record : records) {
            LearningLessonVO learningLesson = BeanUtils.copyBean(record, LearningLessonVO.class);


            CourseSimpleInfoDTO infoDTO = infoDtoMap.get(record.getCourseId());
            if(infoDTO != null) {
                learningLesson.setCourseName(infoDTO.getName());
                learningLesson.setCourseCoverUrl(infoDTO.getCoverUrl());
                learningLesson.setSections(infoDTO.getSectionNum());
            }
            voList.add(learningLesson);
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        // 1.获取当前登录的用户
        Long userId = UserContext.getUser();
        // 2.查询正在学习的课程 select * from xx where user_id = #{userId} AND status = 1 order by latest_learn_time limit 1
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (lesson == null) {
            return null;
        }
        // 3.拷贝PO基础属性到VO
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        // 4.查询课程信息
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cInfo == null) {
            throw new BadRequestException("课程不存在");
        }
        vo.setCourseName(cInfo.getName());
        vo.setCourseCoverUrl(cInfo.getCoverUrl());
        vo.setSections(cInfo.getSectionNum());
        // 5.统计课表中的课程数量 select count(1) from xxx where user_id = #{userId}
        Integer courseAmount = Math.toIntExact(lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .count());
        vo.setCourseAmount(courseAmount);
        // 6.查询小节信息
        List<CataSimpleInfoDTO> cataInfos =
                catalogueClient.batchQueryCatalogue(CollUtils.singletonList(lesson.getLatestSectionId()));
        if (!CollUtils.isEmpty(cataInfos)) {
            CataSimpleInfoDTO cataInfo = cataInfos.get(0);
            vo.setLatestSectionName(cataInfo.getName());
            vo.setLatestSectionIndex(cataInfo.getCIndex());
        }
        return vo;
    }

    @Override
    public void deleteById(Long Id) {
        if(Id == null) {
            return ;
        }
        Long userId = UserContext.getUser();
        LambdaQueryWrapper<LearningLesson> wrapper = new QueryWrapper<LearningLesson>().
                lambda().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, Id);
        if(wrapper == null) {
            return ;
        }
        remove(wrapper);

    }

    @Override
    public Long isHaveLesson(Long courseId) {
        Long userId = UserContext.getUser();
        if(userId == null) {
            return null;
        }
        LearningLesson lesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if(lesson == null) {
            return null;
        }
        System.out.println(lesson);
        System.out.println(lesson.getId());
        return lesson.getId();
    }

    @Override
    public LearningLessonVO queryStatusById(Long courseId) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if(lesson == null) {
            return null;
        }
        LearningLessonVO lessonVO = new LearningLessonVO();
        BeanUtils.copyProperties(lesson, lessonVO);

        return lessonVO;
    }

    @Override
    public Long queryNumberById(Long courseId) {
        Long num = lambdaQuery().eq(LearningLesson::getCourseId, courseId)
                .in(LearningLesson::getStatus,
                        LessonStatus.NOT_BEGIN,
                        LessonStatus.LEARNING,
                        LessonStatus.FINISHED)

                .count();
        return num;
    }


    public LearningLesson queryLessonById(Long courseId) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        return lesson;
    }
    public LearningLesson queryLessonOnlyById(Long courseId) {
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        return lesson;
    }

    @Override
    public void createLearningPlan(Long courseId, Integer freq) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = queryLessonById(courseId);
        AssertUtils.isNotNull(lesson, "课程不存在");
        LearningLesson l = new LearningLesson();
        l.setId(lesson.getId());
        l.setWeekFreq(freq);
        if(lesson.getPlanStatus() == PlanStatus.NO_PLAN) {
            l.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(l);
    }

    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        LearningPlanPageVO result = new LearningPlanPageVO();
        // 当前的登录Id
        Long userId = UserContext.getUser();
        // todo 查询积分

        // 查询本周学习记录的总数量
        LocalDate now = LocalDate.now();
        LocalDateTime begin = DateUtils.getWeekBeginTime(now);
        LocalDateTime end = DateUtils.getWeekEndTime(now);
        List<LearningLesson> lessons = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .list();
        if (CollUtils.isEmpty(lessons)) {
            return null;
        }

        // 查询本周已学习的计划总数量
        List<LearningRecord> learningRecords = recordMapper.selectList(new QueryWrapper<LearningRecord>()
                .lambda()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .lt(LearningRecord::getFinishTime, end)
                .gt(LearningRecord::getFinishTime, begin)
        );
        Map<Long ,Long> countMap = learningRecords.stream().collect(Collectors.groupingBy(LearningRecord::getLessonId, Collectors.counting()));
        // 5.1.本周总的已学习小节数量
        int weekFinished = learningRecords.size();
        result.setWeekFinished(weekFinished);
        // 5.2.本周总的计划学习小节数量
        int weekTotalPlan = lessons.stream().mapToInt(LearningLesson::getWeekFreq).sum();
        result.setWeekTotalPlan(weekTotalPlan);
        // 分页查询
        Page<LearningLesson> p = new Page<>(query.getPageNo(), query.getPageSize(), lessons.size());
        List<LearningLesson> records = CollUtils.sub(lessons, query.from(), query.from() + query.getPageSize());
        if (CollUtils.isEmpty(records)) {
            return result;
        }
        // 6.2.查询课表对应的课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseInfo(records);
        // 6.3.组装数据VO
        List<LearningPlanVO> voList = new ArrayList<>(records.size());
        for (LearningLesson r : records) {
            // 6.4.1.拷贝基础属性到vo
            LearningPlanVO vo = BeanUtils.copyBean(r, LearningPlanVO.class);
            // 6.4.2.填充课程详细信息
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            if (cInfo != null) {
                vo.setCourseName(cInfo.getName());
                vo.setSections(cInfo.getSectionNum());
            }
            // 6.4.3.每个课程的本周已学习小节数量
            vo.setWeekLearnedSections(countMap.getOrDefault(r.getId(), 0L).intValue());
            voList.add(vo);
        }
        return result.pageInfo(p.getTotal(), p.getPages(), voList);
    }
    private Map<Long, CourseSimpleInfoDTO> queryCourseInfo(List<LearningLesson> records) {
        // 3.1.获取课程id
        Set<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        // 3.2.查询课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cIds);
        if (CollUtils.isEmpty(cInfoList)) {
            // 课程不存在，无法添加
            throw new BadRequestException("课程信息不存在！");
        }
        // 3.3.把课程集合处理成Map，key是courseId，值是course本身
        Map<Long, CourseSimpleInfoDTO> cMap = cInfoList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        return cMap;
    }
}
