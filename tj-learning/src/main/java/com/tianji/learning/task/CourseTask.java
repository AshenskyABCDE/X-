package com.tianji.learning.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class CourseTask {
    @Autowired
    private LearningLessonMapper lessonMapper;

    @Autowired
    private ILearningLessonService iLearningLessonService;

    @Scheduled(cron = "0 0/30 * * * ?")
    public void check() {
        Long userId = UserContext.getUser();
        List<LearningLesson> lessons = lessonMapper.selectList(new QueryWrapper<LearningLesson>().lambda()
                .eq(LearningLesson::getUserId, userId)
        );
        for(LearningLesson lesson : lessons) {
            if(lesson.getExpireTime().isBefore(LocalDateTime.now())) {

                iLearningLessonService.lambdaUpdate()
                        .set(LearningLesson::getStatus, LessonStatus.EXPIRED)
                        .eq(LearningLesson::getCourseId, lesson.getCourseId())
                        .eq(LearningLesson::getUserId, userId)
                        .update();
            }
        }
    }

}
