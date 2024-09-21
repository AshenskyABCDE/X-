package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Wrapper;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2024-08-17
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService iLearningLessonService;

    private final CourseClient courseClient;
    private final LearningRecordDelayTaskHandler taskHandler;

    private final RabbitMqHelper mqHelper;
    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        // 先获取课程Id
        Long userId = UserContext.getUser();
        LearningLesson lesson = iLearningLessonService.queryLessonById(courseId);
        if(lesson == null) {
            return null;
        }
        List<LearningRecord> records = lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId()).list();
        LearningLessonDTO lessonDTO = new LearningLessonDTO();
        lessonDTO.setRecords(BeanUtils.copyList(records, LearningRecordDTO.class));
        lessonDTO.setId(lesson.getId());
        lessonDTO.setLatestSectionId(lessonDTO.getLatestSectionId());
        return lessonDTO;
    }

    @Override
    public void addLearningRecord(LearningRecordFormDTO formDTO) {
        Long userId = UserContext.getUser();
        // 处理学习记录
        boolean flag = false;
        if(formDTO.getSectionType() == SectionType.VIDEO) {
            flag = handleVideoRecord(userId,formDTO);
        } else if (formDTO.getSectionType() == SectionType.EXAM) {
            flag = handleExamRecord(userId,formDTO);
        }
        // 处理课表数据
        // 这里主要是判断该课是否学完
        handleLearningLessonsChanges(formDTO, flag);

    }

    private void handleLearningLessonsChanges(LearningRecordFormDTO formDTO, boolean flag) {
        // 获取课表
        LearningLesson lesson = iLearningLessonService.queryLessonById(formDTO.getLessonId());
        if(lesson == null) {
            throw new DbException("课程不存在");
        }
        boolean finished = false;
        if(flag) {
            CourseFullInfoDTO cDTO = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
            if(lesson.getLearnedSections() + 1 >= cDTO.getSectionNum()) {
                finished = true;
            }
        }
        iLearningLessonService.lambdaUpdate()
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(finished, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                .set(!finished, LearningLesson::getLatestSectionId, formDTO.getSectionId())
                .set(!finished, LearningLesson::getLatestLearnTime, formDTO.getCommitTime())
                .setSql(finished, "learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    private boolean handleExamRecord(Long userId, LearningRecordFormDTO formDTO) {
        LearningRecord record = BeanUtils.copyBean(formDTO, LearningRecord.class);
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(formDTO.getCommitTime());
        boolean result = this.save(record);
        if(!result) {
            throw new DbException("新增考试失败");
        }
        return true;
    }

    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO formDTO) {
        // 查询旧的学习记录
        LearningRecord record = lambdaQuery()
                .eq(LearningRecord::getLessonId, formDTO.getLessonId())
                .eq(LearningRecord::getSectionId, formDTO.getSectionId())
                .one();
        // 判断是否存在
        if(record == null) {
            LearningRecord learningRecord = BeanUtils.copyBean(formDTO, LearningRecord.class);
            learningRecord.setUserId(userId);
            record.setFinished(true);
            record.setFinishTime(formDTO.getCommitTime());
            boolean result = this.save(record);
            if(!result) {
                throw new DbException("新增数据失败");
            }
            return false;
        }
        taskHandler.writeRecordCache(record);
        boolean f = true;
        if(record.getFinished() || formDTO.getMoment() * 2 <= formDTO.getDuration()) {
            f = false;
        }
        if(!f) {
            LearningRecord record1 = new LearningRecord();
            record1.setLessonId(formDTO.getLessonId());
            LearningRecord record2 = new LearningRecord();
            record.setLessonId(formDTO.getLessonId());
            record.setSectionId(formDTO.getSectionId());
            record.setMoment(formDTO.getMoment());
            record.setId(record.getId());
            record.setFinished(record.getFinished());
            taskHandler.addLearningRecordTask(record2);
            return false;
        }
        boolean update = lambdaUpdate()
                .set(LearningRecord::getMoment, formDTO.getMoment())
                .set(f, LearningRecord::getFinished, true)
                .set(f, LearningRecord::getFinished, formDTO.getCommitTime())
                .eq(LearningRecord::getId, record.getId())
                .update();
        if(!update) {
            throw new DbException("更新记录失败");
        }
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.LEARN_SECTION,
                10);
        taskHandler.cleanRecordCache(formDTO.getLessonId(),formDTO.getSectionId());
        return f;
    }
}
