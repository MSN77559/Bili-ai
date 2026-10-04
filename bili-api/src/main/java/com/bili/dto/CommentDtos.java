package com.bili.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论模块 DTO（05：API-CM01/CM02/CM03，Phase 3 / FR-IN03~IN05，两级楼中楼）。
 */
public final class CommentDtos {
    private CommentDtos() {}

    /** API-CM01 请求：发评论/回复（parentId=0 父评论；replyToUserId 仅楼中楼内有意义） */
    public static class SendCommentReq {
        private String content;
        private Integer parentId;
        private Long replyToUserId;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Integer getParentId() { return parentId; }
        public void setParentId(Integer parentId) { this.parentId = parentId; }
        public Long getReplyToUserId() { return replyToUserId; }
        public void setReplyToUserId(Long replyToUserId) { this.replyToUserId = replyToUserId; }
    }

    /** 父评论条目（CM02 列表；含 replyCount 与首屏聚合回复） */
    public static class CommentItem {
        public long commentId;
        public long userId;
        public String nickname;
        public String avatarUrl;
        public String content;
        public long parentId;
        /** 有效回复数（Service 批量装配） */
        public long replyCount;
        /** yyyy-MM-dd HH:mm:ss（Service 层格式化） */
        public String createdAt;
        /** 首屏聚合的前 2 条回复（时间正序；replyCount ≤2 时全量） */
        public List<ReplyItem> replies = List.of();

        @com.fasterxml.jackson.annotation.JsonIgnore
        public LocalDateTime createdAtRaw;
    }

    /** 回复条目（CM02 聚合/分页与 CM01 响应共用；replyToNickname 为服务端 JOIN 冗余） */
    public static class ReplyItem {
        public long commentId;
        public long userId;
        public String nickname;
        public String avatarUrl;
        public String content;
        public long parentId;
        /** null=直接回复父评论 */
        public Long replyToUserId;
        /** 回复对象昵称（服务端 JOIN 冗余返回，前端显示「回复 @昵称：」） */
        public String replyToNickname;
        public String createdAt;

        @com.fasterxml.jackson.annotation.JsonIgnore
        public LocalDateTime createdAtRaw;
    }

    /** API-CM02 分页响应（父评论列表或 parent 参数的回复列表共用壳） */
    public static class CommentPageResp {
        public final long total;
        public final int page;
        public final int size;
        public final List<?> list;

        public CommentPageResp(long total, int page, int size, List<?> list) {
            this.total = total;
            this.page = page;
            this.size = size;
            this.list = list;
        }
    }

    /** countRepliesByParents 行映射（parentId → cnt） */
    public static class ReplyCountRow {
        public long parentId;
        public long cnt;
    }
}
