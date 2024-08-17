package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.websocket.server.PathParam;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author author
 * @since 2024-08-16
 */
@RestController
@RequestMapping("/lessons")
@Api(tags = "我的课程的相关接口")
@RequiredArgsConstructor
public class LearningLessonController {
    private final ILearningLessonService iLearningLessonService;
    @ApiOperation("分页查询")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        return iLearningLessonService.queryMyLessons(query);
    }

    @ApiOperation("查询正在学的课程")
    @GetMapping("/now")
    public LearningLessonVO queryMyCurrentLesson() {
        return iLearningLessonService.queryMyCurrentLesson();
    }

    @ApiOperation("根据id删除课程")
    @DeleteMapping("/{courseId}")
    public void deleteById(@PathVariable("courseId") Long id) {
        iLearningLessonService.deleteById(id);
    }

    @ApiOperation("查询用户是否有该课程")
    @GetMapping("/{courseId}/valid")
    public Long isHaveLesson(@PathVariable("courseId") Long courseId) {
        return iLearningLessonService.isHaveLesson(courseId);
    }

    @ApiOperation("查询课程的状态")
    @GetMapping("/{courseId}")
    public LearningLessonVO queryStatusById(@PathVariable("courseId") Long courseId) {
        return iLearningLessonService.queryStatusById(courseId);
    }

    @ApiOperation("统计课堂的学习人数")
    @GetMapping("{courseId}/count")
    public Long queryNumberById(@PathVariable("courseId") Long courseId) {
        return iLearningLessonService.queryNumberById(courseId);
    }

    @ApiOperation("创建学习计划")
    @PostMapping("/plans")
    public void createLearningPlans(@Valid @RequestBody LearningPlanDTO planDTO){
        iLearningLessonService.createLearningPlan(planDTO.getCourseId(), planDTO.getFreq());
    }

    @ApiOperation("查询我的学习计划")
    @GetMapping("/plans")
    public LearningPlanPageVO queryMyPlans(PageQuery query){
        return iLearningLessonService.queryMyPlans(query);
    }
}
