package com.bili.dto;

/**
 * 互动模块 DTO（05：API-IN01~IN06，Phase 3 / FR-IN06~IN10，ADR-18）。
 */
public final class InteractDtos {
    private InteractDtos() {}

    /** API-IN01 响应：点赞切换后最新状态（前端免二次刷新） */
    public static class LikeResp {
        public final boolean liked;
        public final long likeCount;

        public LikeResp(boolean liked, long likeCount) {
            this.liked = liked;
            this.likeCount = likeCount;
        }
    }

    /** API-IN02 响应：互动状态聚合（播放页初始化一次拉齐；游客三状态恒 false） */
    public static class InteractionStatus {
        public boolean liked;
        public boolean coined;
        public boolean favorited;
        public long likeCount;
        public long coinCount;
        public long collectCount;
        /** 本人已投枚数（0/1/2；游客恒 0；投币弹层「已投 n 枚」提示用） */
        public int coinedCount;
        /** 当前收藏所在夹 id（Phase 4 增量字段；未收藏/游客恒 null——弹层初始化勾选用） */
        public Long folderId;
    }

    /** API-IN03 请求：投币枚数（仅 1 或 2） */
    public static class CoinReq {
        private Integer count;

        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
    }

    /** API-IN03 响应：投后余额与最新计数 */
    public static class CoinResp {
        public final int coin;
        public final long coinCount;
        public final int coinedCount;

        public CoinResp(int coin, long coinCount, int coinedCount) {
            this.coin = coin;
            this.coinCount = coinCount;
            this.coinedCount = coinedCount;
        }
    }

    /** API-IN04 响应：收藏切换后最新状态 */
    public static class FavoriteResp {
        public final boolean favorited;
        public final long collectCount;

        public FavoriteResp(boolean favorited, long collectCount) {
            this.favorited = favorited;
            this.collectCount = collectCount;
        }
    }

    /** API-IN04 请求（Phase 4 改造）：folderId 可选——不传 = Phase 3 切换语义；传 = 入夹/移夹 */
    public static class FavoriteReq {
        private Long folderId;

        public Long getFolderId() { return folderId; }
        public void setFolderId(Long folderId) { this.folderId = folderId; }
    }

    /** API-IN07 收藏夹条目（Phase 4 / FR-IN12；count 为夹内有效收藏数实时 COUNT） */
    public static class FolderItem {
        public long folderId;
        public String name;
        public long count;
        public boolean isDefault;
        public String createdAt;

        /** MyBatis 中转字段（DATETIME → LocalDateTime；TINYINT is_default），不出参（@JsonIgnore）
         *  注意 isDefaultRaw 的 getter 名需规避 Jackson 的 is-getter 推断（isXxx() 会被序列化为 defaultRaw），
         *  改名 rawDefault() 后由上面 isDefault 布尔字段正式出参。 */
        @com.fasterxml.jackson.annotation.JsonIgnore
        public java.time.LocalDateTime createdAtRaw;
        @com.fasterxml.jackson.annotation.JsonIgnore
        public int isDefaultRaw;

        public boolean rawDefault() { return isDefaultRaw == 1; }

        public FolderItem() {} // MyBatis 映射需要无参构造
    }

    /** API-IN07 夹列表响应（非分页全量；一人夹数量级极小） */
    public static class FolderListResp {
        public final java.util.List<FolderItem> list;

        public FolderListResp(java.util.List<FolderItem> list) {
            this.list = list;
        }
    }

    /** API-IN08 创建响应（新建夹即时空夹） */
    public static class FolderCreateResp {
        public final long folderId;
        public final String name;
        public final long count;
        public final boolean isDefault;
        public final String createdAt;

        public FolderCreateResp(long folderId, String name, long count, boolean isDefault, String createdAt) {
            this.folderId = folderId;
            this.name = name;
            this.count = count;
            this.isDefault = isDefault;
            this.createdAt = createdAt;
        }
    }

    /** API-IN08/IN09 请求：夹名称（trim 后 1~20 码点） */
    public static class FolderNameReq {
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /** 各夹有效收藏数批查结果行（IN07 内部装配用，不出参；MyBatis 映射需要 public 字段 + 无参构造） */
    public static class FolderCountRow {
        public long folderId;
        public long cnt;

        public FolderCountRow() {}
    }
}
