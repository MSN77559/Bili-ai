package com.bili.service;

import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.dto.UserDtos;
import com.bili.entity.Message;
import com.bili.mapper.MessageMapper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 消息通知服务（FR-MS01~MS03，Phase 4 / ADR-19）。
 * 仅本人（/me 路径不在白名单）；三类埋点写入在各主操作 Service 内同事务调用 {@link #notify}。
 */
@Service
public class MessageService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 内容快照长度上限（TB-11 content VARCHAR(200)） */
    private static final int CONTENT_SNAPSHOT_MAX = 200;

    private final MessageMapper messageMapper;

    public MessageService(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    /**
     * 埋点写入（CM01/IN01/FO01 同事务调用）：
     * 跳过 receiver == sender（自己回复自己/赞自己/关注自己不通知自己，ADR-19）；
     * 快照超 200 字按码点截断（防 VARCHAR(200) 报错——评论上限 500 字可能超）。
     */
    public void notify(long receiverId, long senderId, int type, Long videoId, Long commentId, String content) {
        if (receiverId == senderId) {
            return; // 自发不通知（幂等条件③）
        }
        Message m = new Message();
        m.setReceiverId(receiverId);
        m.setSenderId(senderId);
        m.setType(type);
        m.setVideoId(videoId);
        m.setCommentId(commentId);
        m.setContent(truncate(content));
        messageMapper.insert(m);
    }

    /** 码点安全截断至 200 字（emoji 不劈半） */
    private String truncate(String content) {
        if (content == null) {
            return null;
        }
        return content.codePointCount(0, content.length()) <= CONTENT_SNAPSHOT_MAX
                ? content
                : content.substring(0, content.offsetByCodePoints(0, CONTENT_SNAPSHOT_MAX));
    }

    /** API-MS01 消息列表（type 可选筛选 1/2/3，缺省全部；分页倒序 + commentDeleted 批量标记） */
    public UserDtos.MessagePageResp listMessages(long uid, Integer type, int page, int size) {
        if (type != null && (type < 1 || type > 3)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "消息类型不合法");
        }
        long total = messageMapper.countMessages(uid, type);
        List<UserDtos.MessageItem> list =
                total == 0 ? List.of() : messageMapper.selectMessages(uid, type, (page - 1) * size, size);
        markCommentDeleted(list);
        for (UserDtos.MessageItem item : list) {
            item.createdAt = item.createdAtRaw == null ? null : DT.format(item.createdAtRaw);
            item.createdAtRaw = null; // 出参不带中转字段
        }
        return new UserDtos.MessagePageResp(total, page, size, list);
    }

    /** type=1 条目批量查 comment.deleted（IN 批查防 N+1，05 API-MS01 备注） */
    private void markCommentDeleted(List<UserDtos.MessageItem> list) {
        List<Long> commentIds = list.stream()
                .filter(i -> i.type == Message.TYPE_REPLY && i.commentId != null)
                .map(i -> i.commentId)
                .collect(Collectors.toList());
        if (commentIds.isEmpty()) {
            return;
        }
        Set<Long> deleted = messageMapper.selectCommentDeleted(commentIds).stream()
                .filter(r -> r.deleted == 1)
                .map(r -> r.commentId)
                .collect(Collectors.toSet());
        if (deleted.isEmpty()) {
            return;
        }
        for (UserDtos.MessageItem item : list) {
            if (item.type == Message.TYPE_REPLY && deleted.contains(item.commentId)) {
                item.commentDeleted = true;
            }
        }
    }

    /** API-MS02 单条已读（幂等）。不存在或非本人 → 统一 40406（不暴露存在性，CM03 范式） */
    public void markRead(long uid, long messageId) {
        if (messageId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "消息 ID 不合法");
        }
        Message m = messageMapper.selectByIdDirect(messageId);
        if (m == null || m.getReceiverId() != uid) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        messageMapper.markRead(messageId);
    }

    /** API-MS03 全部已读（幂等） */
    public void markAllRead(long uid) {
        messageMapper.markAllRead(uid);
    }

    /** API-MS04 未读数（实时 COUNT） */
    public UserDtos.UnreadCountResp unreadCount(long uid) {
        return new UserDtos.UnreadCountResp(messageMapper.countUnread(uid));
    }
}
