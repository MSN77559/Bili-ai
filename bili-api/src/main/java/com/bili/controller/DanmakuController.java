package com.bili.controller;

import com.bili.common.context.UserContext;
import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.common.result.Result;
import com.bili.dto.DanmakuDtos;
import com.bili.service.DanmakuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 弹幕接口（05：API-DL01/DL02，Phase 3 / FR-IN01/IN02）。
 * GET /api/videos/{videoId}/danmaku 为白名单公开接口（无视角字段，token 可不带）；
 * POST 需登录（白名单的 videos 子路径放行带 GET 方法判断，POST 不放行）。
 */
@RestController
@RequestMapping("/api/videos/{videoId}/danmaku")
public class DanmakuController {

    private final DanmakuService danmakuService;

    public DanmakuController(DanmakuService danmakuService) {
        this.danmakuService = danmakuService;
    }

    /** API-DL01 发弹幕（登录） */
    @PostMapping
    public Result<DanmakuDtos.SendDanmakuResp> send(@PathVariable Long videoId,
                                                    @RequestBody DanmakuDtos.SendDanmakuReq req) {
        long vid = requireVideoId(videoId);
        return Result.ok(danmakuService.send(vid, requireUid(), req));
    }

    /** API-DL02 弹幕列表（公开白名单；time_offset 升序全量，无分页） */
    @GetMapping
    public Result<DanmakuDtos.DanmakuListResp> list(@PathVariable Long videoId) {
        long vid = requireVideoId(videoId);
        return Result.ok(danmakuService.list(vid));
    }

    private long requireVideoId(Long videoId) {
        if (videoId == null || videoId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "视频 ID 不合法");
        }
        return videoId;
    }

    private long requireUid() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED); // 理论不可达（拦截器保证）
        }
        return uid;
    }
}
