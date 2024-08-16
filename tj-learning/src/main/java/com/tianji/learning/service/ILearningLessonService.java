package com.tianji.learning.service;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author author
 * @since 2024-08-16
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    void addUserLessons(OrderBasicDTO dto);

    PageDTO<LearningLessonVO> queryMyLessons(PageQuery query);

    LearningLessonVO queryMyCurrentLesson();

    void deleteById(Long id);

    Long isHaveLesson(Long courseId);

    LearningLessonVO queryStatusById(Long courseId);

    Long queryNumberById(Long courseId);
}
