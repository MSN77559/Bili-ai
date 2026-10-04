package com.bili.dto;

import java.util.List;

/**
 * 搜索模块 DTO（05：API-SE01/SE02，Phase 4）。
 * 条目复用既有范式：视频=VideoDtos.VideoItem（VD02 卡片含 UP 摘要）、用户=UserDtos.FollowPageItem（FO03 条目含 isFollowed 视角）。
 */
public final class SearchDtos {
    private SearchDtos() {}

    /** API-SE01 视频搜索分页响应（05 §1 分页约定；条目字段同 VD02 卡片） */
    public static class VideoSearchResp {
        public final long total;
        public final int page;
        public final int size;
        public final List<VideoDtos.VideoItem> list;

        public VideoSearchResp(long total, int page, int size, List<VideoDtos.VideoItem> list) {
            this.total = total;
            this.page = page;
            this.size = size;
            this.list = list;
        }
    }

    /** API-SE02 用户搜索分页响应（条目字段同 FO03） */
    public static class UserSearchResp {
        public final long total;
        public final int page;
        public final int size;
        public final List<UserDtos.FollowPageItem> list;

        public UserSearchResp(long total, int page, int size, List<UserDtos.FollowPageItem> list) {
            this.total = total;
            this.page = page;
            this.size = size;
            this.list = list;
        }
    }
}
