package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
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
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
