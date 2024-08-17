package com.tianji.learning.controller;


import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author author
 * @since 2024-08-17
 */
@Api(tags = "学习记录接口")
@RestController
@RequestMapping("/learning-records")
@RequiredArgsConstructor
public class LearningRecordController {
    private final ILearningRecordService recordService;


    @GetMapping("/course/{courseId}")
    @ApiOperation("查询当前课程的学习状态")
    LearningLessonDTO queryLearningRecordByCourse(@PathVariable("courseId") Long courseId) {
        return recordService.queryLearningRecordByCourse(courseId);
    }

    @ApiOperation("提交学习记录")
    @PostMapping
    public void addLearningRecord(@RequestBody @Validated LearningRecordFormDTO formDTO){

        recordService.addLearningRecord(formDTO);
    }
}
