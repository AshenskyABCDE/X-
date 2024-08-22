package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author author
 * @since 2024-08-21
 */
@RestController
@Api(tags = "互动问题相关接口管理端")
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
public class InteractionQuestionAdminController {

    private final IInteractionQuestionService questionService;

    @ApiOperation("管理端分页查询互动问题")
    @GetMapping("page")
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query){
        return questionService.queryQuestionPageAdmin(query);
    }

    @ApiOperation("隐藏相应的提问")
    @PutMapping("/{id}/hidden/{hidden}")
    public void updateQuestion(@PathVariable("id") Long id, @PathVariable("hidden") Boolean hidden) {
        questionService.updateHiddenQuestion(id,hidden);
    }

    @ApiOperation("根据id查询问题")
    @GetMapping("{id}")
    public QuestionAdminVO queryById(@PathVariable("id") Long id) {
        return questionService.queryById(id);
    }

}
