package com.bili.controller;

import com.bili.common.context.UserContext;
import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.common.result.Result;
import com.bili.dto.CommentDtos;
import com.bili.service.CommentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论接口（05：API-CM01/CM02/CM03，Phase 3 / FR-IN03~IN05，两级楼中楼）。
 * GET /api/videos/{videoId}/comments 为白名单公开接口；POST 与 DELETE /api/comments/{id} 需登录。
 */
@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /** API-CM01 发评论/回复（登录） */
    @PostMapping("/api/videos/{videoId}/comments")
    public Result<CommentDtos.ReplyItem> send(@PathVariable Long videoId,
                                              @RequestBody CommentDtos.SendCommentReq req) {
        if (videoId == null || videoId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "视频 ID 不合法");
        }
        return Result.ok(commentService.send(videoId, requireUid(), req));
    }

    /** API-CM02 评论列表（公开分页；parent 缺省=父评论列表，传值=回复分页） */
    @GetMapping("/api/videos/{videoId}/comments")
    public Result<CommentDtos.CommentPageResp> list(@PathVariable Long videoId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(required = false) Long parent) {
        if (videoId == null || videoId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "视频 ID 不合法");
        }
        validatePage(page, size);
        if (parent != null && parent <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "评论参数不合法");
        }
        return Result.ok(commentService.list(videoId, page, size, parent));
    }

    /** API-CM03 删除评论（仅本人；父删级联逻辑删回复） */
    @DeleteMapping("/api/comments/{commentId}")
    public Result<Void> delete(@PathVariable Long commentId) {
        if (commentId == null || commentId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "评论 ID 不合法");
        }
        commentService.delete(commentId, requireUid());
        return Result.ok();
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "分页参数不合法");
        }
    }

    private long requireUid() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return uid;
    }
}
