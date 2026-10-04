package com.bili.dto;

import java.util.List;

/**
 * 视频模块 DTO（05：API-VD01~VD04，Phase 2）。
 */
public final class VideoDtos {
    private VideoDtos() {}

    /** API-VD01 响应：投稿成功（前端跳 /video/:videoId） */
    public static class PublishResp {
        public final long videoId;
        public final String videoUrl;
        /** 未上传封面时为 null（前端渐变兜底，ADR-17） */
        public final String coverUrl;

        public PublishResp(long videoId, String videoUrl, String coverUrl) {
            this.videoId = videoId;
            this.videoUrl = videoUrl;
            this.coverUrl = coverUrl;
        }
    }

    /** API-VD02/VD04 列表条目（VD04 不含 up 字段——空间主人页头已知） */
    public static class VideoItem {
        public long videoId;
        public long userId;
        /** UP 昵称（仅 VD02 返回；VD04 不填） */
        public String upNickname;
        /** UP 头像（仅 VD02 返回；VD04 不填） */
        public String upAvatarUrl;
        public String title;
        public int category;
        /** null=未上传封面，前端按 videoId 取模走 GRADIENTS 渐变兜底（ADR-17） */
        public String coverUrl;
        public int duration;
        public long viewCount;
        public long likeCount;
        /** yyyy-MM-dd HH:mm:ss（Service 层手动格式化，与 MyProfile.createdAt 同款） */
        public String createdAt;

        /** MyBatis 中转字段（DATETIME → LocalDateTime），不出参（@JsonIgnore） */
        @com.fasterxml.jackson.annotation.JsonIgnore
        public java.time.LocalDateTime createdAtRaw;

        public VideoItem() {} // MyBatis 映射需要无参构造
    }

    /** API-VD02/VD04 分页响应（05 §1 分页约定） */
    public static class VideoPageResp {
        public final long total;
        public final int page;
        public final int size;
        public final List<VideoItem> list;

        public VideoPageResp(long total, int page, int size, List<VideoItem> list) {
            this.total = total;
            this.page = page;
            this.size = size;
            this.list = list;
        }
    }

    /** API-VD03 UP 主摘要（脱敏：不含 email 等字段） */
    public static class UpSummary {
        public long userId;
        public String nickname;
        public String avatarUrl;
        public String sign;
        public long followerCount;
    }

    /** API-VD03 视频详情（含 UP 摘要与视角；viewCount 为自增后的值） */
    public static class VideoDetail {
        public long videoId;
        public long userId;
        public String title;
        public int category;
        public String description;
        public String videoUrl;
        public String coverUrl;
        public int duration;
        public long viewCount;
        public long likeCount;
        public String createdAt;
        public UpSummary up;
        /** 视角字段（US02 范式）：游客恒 false */
        public boolean isSelf;
        public boolean isFollowed;
    }
}
