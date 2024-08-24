package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author author
 * @since 2024-08-24
 */
@Api(tags = "点赞相关接口")
@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
@Slf4j
public class LikedRecordController {

    private final ILikedRecordService likedRecordService;
    @ApiOperation("点赞或取消赞")
    @PostMapping
    public void addLikeRecord(@Valid @RequestBody LikeRecordFormDTO dto) {
        log.info("传入数据{}",dto);
        likedRecordService.addLikeRecord(dto);
    }


    @ApiOperation("返回点赞状态")
    @GetMapping("/list")
    public Set<Long> getLikesStatusByBizIds(@RequestParam("bizIds") List<Long> bizIds) {
        return likedRecordService.getLikesStatusByBizIds(bizIds);
    }
}
