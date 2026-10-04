package com.bili.service;

import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.dto.CommentDtos;
import com.bili.entity.Comment;
import com.bili.entity.Message;
import com.bili.entity.Video;
import com.bili.mapper.CommentMapper;
import com.bili.mapper.VideoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论服务（FR-IN03~IN05，Phase 3，两级楼中楼；Phase 4 加消息埋点）。
 */
@Service
public class CommentService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CommentMapper commentMapper;
    private final VideoMapper videoMapper;
    private final MessageService messageService;

    public CommentService(CommentMapper commentMapper, VideoMapper videoMapper, MessageService messageService) {
        this.commentMapper = commentMapper;
        this.videoMapper = videoMapper;
        this.messageService = messageService;
    }

    /** API-CM01 发评论/回复（登录）。parentId 非 0 时校验父评论存在且同视频（40404）。
     * Phase 4 起楼中楼回复同事务埋点消息通知（FR-MS01，ADR-19）。 */
    @Transactional
    public CommentDtos.ReplyItem send(long videoId, long uid, CommentDtos.SendCommentReq req) {
        requireVideo(videoId);
        String content = req.getContent() == null ? "" : req.getContent().trim();
        int len = content.codePointCount(0, content.length());
        if (len < 1 || len > 500) {
            throw new BusinessException(ErrorCode.CONTENT_INVALID, "评论长度须为 1-500 字符");
        }
        long parentId = req.getParentId() == null ? 0 : req.getParentId();
        if (parentId < 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "评论参数不合法");
        }
        if (req.getReplyToUserId() != null && req.getReplyToUserId() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "回复对象不合法");
        }
        Comment parent = null;
        if (parentId != 0) {
            // 楼中楼：父评论必须存在、属于同视频、且自身是父评论行（parent_id=0）
            parent = commentMapper.selectById(parentId); // @TableLogic 过滤 deleted=1
            if (parent == null || parent.getVideoId() != videoId || parent.getParentId() != 0) {
                throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
            }
        }
        Comment c = new Comment();
        c.setVideoId(videoId);
        c.setUserId(uid); // token uid
        c.setParentId(parentId);
        c.setReplyToUserId(req.getReplyToUserId()); // 可 null
        c.setContent(content);
        // like_count 取列默认（预留水位不接写路径）
        commentMapper.insert(c);

        // 消息埋点（FR-MS01，Phase 4 / ADR-19）：仅楼中楼回复产生「回复我的」；
        // 通知对象：reply_to_user_id 非空通知该用户，否则通知父评论作者；
        // 快照 = 回复内容前 200 字（MessageService 截断）；notify 内跳过 receiver == sender（自发不通知）
        if (parent != null) {
            long receiver = req.getReplyToUserId() != null ? req.getReplyToUserId() : parent.getUserId();
            messageService.notify(receiver, uid, Message.TYPE_REPLY, videoId, c.getId(), content);
        }
        // 返回与 CM02 条目一致的完整摘要（单条 JOIN 回读，含本人昵称头像与 replyTo 昵称冗余）
        CommentDtos.ReplyItem item = commentMapper.selectCommentSummary(c.getId());
        if (item != null) {
            // createdAtRaw → createdAt（与 CM02 同款格式化，漏掉会返回 null 时间）
            item.createdAt = item.createdAtRaw == null ? null : DT.format(item.createdAtRaw);
            item.createdAtRaw = null;
        }
        if (item == null) {
            // 理论不可达（刚插入）；防御式兜底
            item = new CommentDtos.ReplyItem();
            item.commentId = c.getId();
            item.userId = uid;
            item.content = content;
            item.parentId = parentId;
            item.replyToUserId = c.getReplyToUserId();
            item.createdAt = c.getCreatedAt() == null ? null : DT.format(c.getCreatedAt());
        }
        return item;
    }

    /**
     * API-CM02 评论列表（公开分页）。parent 缺省=父评论分页（倒序 + replyCount + 首屏 2 条回复聚合）；
     * parent 传值=该父评论的回复分页（正序）。批量装配防 N+1（05 §7 注意事项 13）。
     */
    public CommentDtos.CommentPageResp list(long videoId, int page, int size, Long parent) {
        requireVideo(videoId);
        int offset = (page - 1) * size;
        if (parent == null) {
            long total = commentMapper.countParents(videoId);
            List<CommentDtos.CommentItem> list = total == 0
                    ? List.of()
                    : commentMapper.selectParents(videoId, offset, size);
            if (!list.isEmpty()) {
                assembleReplies(list);
            }
            for (CommentDtos.CommentItem item : list) {
                item.createdAt = item.createdAtRaw == null ? null : DT.format(item.createdAtRaw);
                item.createdAtRaw = null;
            }
            return new CommentDtos.CommentPageResp(total, page, size, list);
        }
        // 回复分页：parent 必须指向本视频下的父评论行
        Comment parentNode = commentMapper.selectById(parent);
        if (parentNode == null || parentNode.getVideoId() != videoId || parentNode.getParentId() != 0) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        long total = commentMapper.countReplies(parent);
        List<CommentDtos.ReplyItem> list = total == 0
                ? List.of()
                : commentMapper.selectReplies(parent, offset, size);
        formatReplies(list);
        return new CommentDtos.CommentPageResp(total, page, size, list);
    }

    /**
     * API-CM03 删除评论（仅本人）。父评论删除时同事务级联逻辑删其全部回复（FR-IN05 主流程 5）。
     * 无权/不存在共用 40404（不暴露他人评论存在性）。
     */
    @Transactional
    public void delete(long commentId, long uid) {
        Comment comment = commentMapper.selectById(commentId); // @TableLogic 过滤已删
        if (comment == null || comment.getUserId() != uid) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        // @TableLogic 实体禁止 updateById(setDeleted)——MP 会将 deleted 排除出 SET 且拼入 WHERE，
        // 生成空 SET 非法 SQL；deleteById 自动生成 UPDATE ... SET deleted=1 WHERE id=? AND deleted=0
        commentMapper.deleteById(commentId);
        if (comment.getParentId() == 0) {
            commentMapper.cascadeDeleteReplies(commentId); // 级联逻辑删回复
        }
    }

    /** 父评论页的 replyCount + 首屏 2 条回复批量装配（防 N+1：两次批量 SQL） */
    private void assembleReplies(List<CommentDtos.CommentItem> parents) {
        List<Long> parentIds = parents.stream().map(c -> c.commentId).collect(Collectors.toList());
        Map<Long, Long> countMap = commentMapper.countRepliesByParents(parentIds).stream()
                .collect(Collectors.toMap(r -> r.parentId, r -> r.cnt));
        Map<Long, List<CommentDtos.ReplyItem>> replyMap = commentMapper.selectFirstReplies(parentIds).stream()
                .collect(Collectors.groupingBy(r -> r.parentId));
        for (CommentDtos.CommentItem p : parents) {
            p.replyCount = countMap.getOrDefault(p.commentId, 0L);
            List<CommentDtos.ReplyItem> replies = replyMap.get(p.commentId);
            if (replies != null) {
                formatReplies(replies);
                p.replies = replies;
            }
        }
    }

    private void formatReplies(List<CommentDtos.ReplyItem> replies) {
        for (CommentDtos.ReplyItem r : replies) {
            r.createdAt = r.createdAtRaw == null ? null : DT.format(r.createdAtRaw);
            r.createdAtRaw = null;
        }
    }

    private Video requireVideo(long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        return video;
    }
}
