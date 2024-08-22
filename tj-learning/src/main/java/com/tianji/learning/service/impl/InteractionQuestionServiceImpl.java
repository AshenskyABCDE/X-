package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.management.Query;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author author
 * @since 2024-08-21
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final InteractionReplyMapper replyMapper;
    private final UserClient userClient;
    private final SearchClient searchClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;
    @Override
    public void saveQuestion(QuestionFormDTO questionDTO) {
        Long userId = UserContext.getUser();

        InteractionQuestion interactionQuestion =  BeanUtils.toBean(questionDTO, InteractionQuestion.class);
        interactionQuestion.setUserId(userId);
        save(interactionQuestion);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO dto) {
        // 校验参数
        if (StringUtils.isBlank(dto.getTitle()) || StringUtils.isBlank(dto.getDescription()) || dto.getAnonymity() == null) {
            throw new DbException("传递了非法参数");
        }
        Long userId = UserContext.getUser();
        InteractionQuestion one = lambdaQuery()
                .eq(InteractionQuestion::getUserId, userId)
                .eq(InteractionQuestion::getId, id)
                .one();
        if(one == null) {
            throw new DbException("传递了非法参数");
        }
        one.setTitle(dto.getTitle());
        one.setDescription(dto.getDescription());
        one.setAnonymity(dto.getAnonymity());
        this.updateById(one);
    }

    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        // 参数校验 里面的课程id和小节id 不能丢
        if(query.getCourseId() == null && query.getSectionId() == null) {
            throw new BadRequestException("课程id和小节id不能为空");
        }
        // 分页查询
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();
        Long userId = UserContext.getUser();
        Page<InteractionQuestion> page = lambdaQuery()
                .select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, UserContext.getUser())
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 根据id查询提问者的最近一次回答的信息
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        for(InteractionQuestion q : records) {
            if(!q.getAnonymity()) {
                userIds.add(q.getUserId());
            }
            answerIds.add(q.getLatestAnswerId());
        }
        answerIds.remove(null);
        Map<Long , InteractionReply> replyMap = new HashMap<>(answerIds.size());
        if(CollUtils.isNotEmpty(answerIds)) {
            List<InteractionReply> replies = replyMapper.selectBatchIds(answerIds);
            for(InteractionReply reply : replies) {
                replyMap.put(reply.getId(), reply);
                if(!reply.getAnonymity()) {
                    userIds.add(reply.getUserId());
                }
            }
        }

        userIds.remove(null);
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if(CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        List<QuestionVO> voList = new ArrayList<>(records.size());
        for(InteractionQuestion r : records) {
            QuestionVO vo = BeanUtils.copyBean(r, QuestionVO.class);
            vo.setUserId(null);
            // 封装提问者信息
            if(!r.getAnonymity()) {
                UserDTO userDTO = userMap.get(r.getUserId());
                if(userDTO != null) {
                    vo.setUserId(userDTO.getId());
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }

            // 封装最后一次信息
            InteractionReply reply = replyMap.get(r.getUserId());
            if(reply != null) {
                vo.setLatestReplyContent(reply.getContent());
                if(!reply.getAnonymity()) {
                    UserDTO userDTO = userMap.get(reply.getUserId());
                    vo.setLatestReplyUser(userDTO.getName());
                }
            }
            voList.add(vo);
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        InteractionQuestion byId = getById(id);
        if(byId == null || byId.getHidden()) {
            return null;
        }
        UserDTO user = null;
        if(!byId.getAnonymity()){
            user = userClient.queryUserById(byId.getUserId());
        }
        // 4.封装VO
        QuestionVO vo = BeanUtils.copyBean(byId, QuestionVO.class);
        if (user != null) {
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());
        }
        return vo;
    }

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        // 处理课程名称
        List<Long> courseIds = null;
        if(StringUtils.isNotBlank(query.getCourseName())) {
            courseIds = searchClient.queryCoursesIdByName(query.getCourseName());
            if(CollUtils.isEmpty(courseIds)) {
                return PageDTO.empty(0L, 0L);
            }
        }
        // 分页查询
        Integer status = query.getStatus();
        LocalDateTime begin = query.getBeginTime();
        LocalDateTime end = query.getEndTime();
        Page<InteractionQuestion> page = this.lambdaQuery()
                .in(courseIds != null, InteractionQuestion::getCourseId, courseIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .gt(begin != null, InteractionQuestion::getCreateTime, begin)
                .lt(end != null, InteractionQuestion::getCreateTime, end)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 准备用户数据 课程数据 章节数据
        Set<Long> userIds = new HashSet<>();
        Set<Long> cIds = new HashSet<>();
        Set<Long> cataIds = new HashSet<>();

        for ( InteractionQuestion q : records) {
            userIds.add(q.getUserId());
            cIds.add(q.getCourseId());
            cataIds.add(q.getChapterId());
            cataIds.add(q.getSectionId());
        }

        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if(CollUtils.isNotEmpty(users)) {
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        // 根据id查询课程
        List<CourseSimpleInfoDTO> cInfos = courseClient.getSimpleInfoList(cIds);
        Map<Long, CourseSimpleInfoDTO> cInfoMap = new HashMap<>(cIds.size());
        if(CollUtils.isNotEmpty(cInfos)) {
            cInfoMap = cInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        }

        // 根据id查询章节
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(cataIds);
        Map<Long, String> cataMap = new HashMap<>(cataIds.size());
        if (CollUtils.isNotEmpty(catas)) {
            cataMap = catas.stream()
                    .collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }

        List<QuestionAdminVO> voList = new ArrayList<>(records.size());
        for(InteractionQuestion q : records) {
            QuestionAdminVO vo = BeanUtils.copyBean(q, QuestionAdminVO.class);
            UserDTO userDTO = userMap.get(q.getUserId());
            if(userDTO != null) {
                vo.setUserName(userDTO.getName());
            }

            CourseSimpleInfoDTO infoDTO = cInfoMap.get(q.getCourseId());
            if(infoDTO != null) {
                vo.setCourseName(infoDTO.getName());
                vo.setCategoryName(categoryCache.getCategoryNames(infoDTO.getCategoryIds()));
            }
            vo.setChapterName(cataMap.getOrDefault(q.getChapterId(), ""));
            vo.setSectionName(cataMap.getOrDefault(q.getSectionId(), ""));
            voList.add(vo);
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public void deleteQuestion(Long id) {
        InteractionQuestion byId = getById(id);
        if(byId == null) {
            throw new DbException("访问的是一个不存在的");
        }
        if(!byId.getUserId().equals(UserContext.getUser())) {
            throw new DbException("删除问题的用户和登录用户不一致");
        }
        // select * from ... where quesetion_id = #{id}
        List<InteractionReply> replies = replyMapper.selectList(
                new QueryWrapper<InteractionReply>().eq("question_id", id)
        );
        removeById(id);
        for(InteractionReply q : replies) {
            replyMapper.deleteById(q.getId());
        }
    }
}
