package com.bili.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户/关注模块 DTO（05：API-US01~US04、API-FO01/02）。
 * PUT /api/users/me 不定义 email 字段（ADR-08：多余字段静默忽略）。
 */
public final class UserDtos {
    private UserDtos() {}

    /** API-US01 响应：当前用户完整资料（唯一含 email 的接口） */
    public static class MyProfile {
        public long userId;
        public String email;
        public String nickname;
        public String avatarUrl;
        /** 空间背景图（FR-PF07）：未上传时为列默认值 default.png（编辑页回显默认图） */
        public String bannerUrl;
        public int gender;
        public String birthday;   // yyyy-MM-dd 或 null
        public String sign;
        public boolean showContribute; // 空间展示开关（FR-PF05）
        public boolean showFavorites;
        public boolean showLikes;
        public boolean showBirthday;  // 生日公开开关原值（FR-PF06，编辑页回显用）
        public boolean showFollows;   // 关注/粉丝列表公开开关原值（FR-FO06，Phase 4，编辑页回显用）
        public int coin;          // 硬币余额（FR-PF08）：本期恒 0（获取/消费 Phase 3）
        public String createdAt;  // yyyy-MM-dd HH:mm:ss
    }

    /** API-US02 响应：公开主页信息（不含 email/createdAt，05 §2.3 脱敏 + FR-PR01 精简） */
    public static class PublicProfile {
        public long userId;
        public String nickname;
        public String avatarUrl;
        /** 空间背景图（FR-PF07）：未上传时为默认图 default.png；对所有访客公开 */
        public String bannerUrl;
        public int gender;
        /** 主人关闭生日公开（FR-PF06）且访问者非本人时为 null（与未设置不可区分，刻意设计） */
        public String birthday;
        public String sign;
        public long followingCount;
        public long followerCount;
        public boolean isSelf;
        public boolean isFollowed;
        /** 当前访问者是否已拉黑 TA（FR-BK01；单向视角——被拉黑方恒 false，不告知） */
        public boolean isBlocked;
        /** 空间 tab 可见性（FR-PF05）：本人视角恒 true；他人/游客按主人设置返回 */
        public boolean showContribute;
        public boolean showFavorites;
        public boolean showLikes;
    }

    /** API-US03 请求：修改基础资料（四字段，FR-PF02） */
    public static class UpdateProfileReq {
        @NotBlank(message = "昵称不能为空")
        @Size(min = 2, max = 16, message = "昵称长度须为 2-16 字符")
        private String nickname;

        @NotNull(message = "性别不能为空")
        @Min(value = 0, message = "性别参数不合法")
        @Max(value = 2, message = "性别参数不合法")
        private Integer gender;

        /** yyyy-MM-dd，可 null=清空；不晚于当天由 Service 校验 */
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "生日日期不合法")
        private String birthday;

        @NotNull(message = "签名不能为空")
        @Size(max = 100, message = "签名最长 100 字符")
        private String sign;

        /** 空间展示开关（FR-PF05）：null=不改该字段；仅接受 0/1 */
        @Min(value = 0, message = "空间展示参数不合法")
        @Max(value = 1, message = "空间展示参数不合法")
        private Integer showContribute;
        @Min(value = 0, message = "空间展示参数不合法")
        @Max(value = 1, message = "空间展示参数不合法")
        private Integer showFavorites;
        @Min(value = 0, message = "空间展示参数不合法")
        @Max(value = 1, message = "空间展示参数不合法")
        private Integer showLikes;
        /** 生日公开开关（FR-PF06）：null=不改该字段；仅接受 0/1 */
        @Min(value = 0, message = "生日公开参数不合法")
        @Max(value = 1, message = "生日公开参数不合法")
        private Integer showBirthday;
        /** 关注/粉丝列表公开开关（FR-FO06，Phase 4）：null=不改该字段；仅接受 0/1 */
        @Min(value = 0, message = "关注列表公开参数不合法")
        @Max(value = 1, message = "关注列表公开参数不合法")
        private Integer showFollows;

        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public Integer getGender() { return gender; }
        public void setGender(Integer gender) { this.gender = gender; }
        public String getBirthday() { return birthday; }
        public void setBirthday(String birthday) { this.birthday = birthday; }
        public String getSign() { return sign; }
        public void setSign(String sign) { this.sign = sign; }
        public Integer getShowContribute() { return showContribute; }
        public void setShowContribute(Integer showContribute) { this.showContribute = showContribute; }
        public Integer getShowFavorites() { return showFavorites; }
        public void setShowFavorites(Integer showFavorites) { this.showFavorites = showFavorites; }
        public Integer getShowLikes() { return showLikes; }
        public void setShowLikes(Integer showLikes) { this.showLikes = showLikes; }
        public Integer getShowBirthday() { return showBirthday; }
        public void setShowBirthday(Integer showBirthday) { this.showBirthday = showBirthday; }
        public Integer getShowFollows() { return showFollows; }
        public void setShowFollows(Integer showFollows) { this.showFollows = showFollows; }
    }

    /** API-US04 响应 */
    public static class AvatarResp {
        public final String avatarUrl;
        public AvatarResp(String avatarUrl) { this.avatarUrl = avatarUrl; }
    }

    /** API-US05 响应（FR-PF07） */
    public static class BannerResp {
        public final String bannerUrl;
        public BannerResp(String bannerUrl) { this.bannerUrl = bannerUrl; }
    }

    /** API-FO01/02 响应：操作后最新状态（免二次刷新） */
    public static class FollowResp {
        public final boolean isFollowed;
        public final long followerCount;
        public FollowResp(boolean isFollowed, long followerCount) {
            this.isFollowed = isFollowed;
            this.followerCount = followerCount;
        }
    }

    /** API-BK01/02 响应：操作后最新状态（目标用户最新粉丝数） */
    public static class BlockResp {
        public final boolean isBlocked;
        public final long followerCount;
        public BlockResp(boolean isBlocked, long followerCount) {
            this.isBlocked = isBlocked;
            this.followerCount = followerCount;
        }
    }

    /** API-FO03/FO04 列表条目（FR-FO05）；isFollowed 为访问者视角（游客恒 false，本人行恒 false） */
    public static class FollowPageItem {
        public long userId;
        public String nickname;
        public String avatarUrl;
        public String sign;
        public boolean isFollowed;

        public FollowPageItem() {} // MyBatis 映射需要无参构造
    }

    /** API-FO03/FO04 分页响应（05 §1 分页约定：{total, page, size, list}） */
    public static class FollowPageResp {
        public final long total;
        public final int page;
        public final int size;
        public final java.util.List<FollowPageItem> list;

        public FollowPageResp(long total, int page, int size, java.util.List<FollowPageItem> list) {
            this.total = total;
            this.page = page;
            this.size = size;
            this.list = list;
        }
    }

    /** API-US06 硬币流水条目（Phase 3 / FR-IN11）；bizType 前端中文映射（1 签到/2 注册赠送/3 投币支出） */
    public static class CoinFlowItem {
        public long flowId;
        /** 正=收入、负=支出（前端正负色区分） */
        public int changeAmount;
        public int bizType;
        /** yyyy-MM-dd HH:mm:ss（Service 层格式化） */
        public String createdAt;

        @com.fasterxml.jackson.annotation.JsonIgnore
        public java.time.LocalDateTime createdAtRaw;

        public CoinFlowItem() {} // MyBatis 映射需要无参构造
    }

    /** API-US06 分页响应（倒序） */
    public static class CoinFlowPageResp {
        public final long total;
        public final int page;
        public final int size;
        public final java.util.List<CoinFlowItem> list;

        public CoinFlowPageResp(long total, int page, int size, java.util.List<CoinFlowItem> list) {
            this.total = total;
            this.page = page;
            this.size = size;
            this.list = list;
        }
    }

    /** API-HI01 历史条目（Phase 4 / FR-HI02；视频摘要 + UP 信息，viewedAt 为最后观看时间） */
    public static class HistoryItem {
        public long videoId;
        public String title;
        /** null=未上传封面，前端渐变兜底（ADR-17） */
        public String coverUrl;
        public int duration;
        public long upUserId;
        public String upNickname;
        public String viewedAt;

        /** MyBatis 中转字段（DATETIME → LocalDateTime），不出参（@JsonIgnore） */
        @com.fasterxml.jackson.annotation.JsonIgnore
        public java.time.LocalDateTime viewedAtRaw;

        public HistoryItem() {} // MyBatis 映射需要无参构造
    }

    /** API-HI01 历史分页响应（viewed_at 倒序） */
    public static class HistoryPageResp {
        public final long total;
        public final int page;
        public final int size;
        public final java.util.List<HistoryItem> list;

        public HistoryPageResp(long total, int page, int size, java.util.List<HistoryItem> list) {
            this.total = total;
            this.page = page;
            this.size = size;
            this.list = list;
        }
    }

    /** API-MS01 消息条目（Phase 4 / FR-MS01；sender JOIN user 实时摘要，content 为评论快照） */
    public static class MessageItem {
        public long id;
        /** 1 回复我的 / 2 收到的赞 / 3 新粉丝（前端按此拼文案与跳转目标） */
        public int type;
        public long senderId;
        public String senderNickname;
        public String senderAvatarUrl;
        /** type=1/2 跳转目标 /video/{id}；type=3 为 null */
        public Long videoId;
        /** 仅 type=1 */
        public Long commentId;
        /** 评论内容快照（仅 type=1，前 200 字） */
        public String content;
        /** 仅 type=1 有意义：被回复评论已删除 → 前端置灰「该评论已删除」（消息不删，ADR-19） */
        public boolean commentDeleted;
        /** 未读圆点渲染依据 */
        public boolean isRead;
        public String createdAt;

        /** MyBatis 中转字段（DATETIME → LocalDateTime），不出参（@JsonIgnore） */
        @com.fasterxml.jackson.annotation.JsonIgnore
        public java.time.LocalDateTime createdAtRaw;

        public MessageItem() {} // MyBatis 映射需要无参构造
    }

    /** API-MS01 消息分页响应（created_at 倒序） */
    public static class MessagePageResp {
        public final long total;
        public final int page;
        public final int size;
        public final java.util.List<MessageItem> list;

        public MessagePageResp(long total, int page, int size, java.util.List<MessageItem> list) {
            this.total = total;
            this.page = page;
            this.size = size;
            this.list = list;
        }
    }

    /** API-MS04 未读数响应（NavBar 红点专用轻量端点） */
    public static class UnreadCountResp {
        public final long unreadCount;

        public UnreadCountResp(long unreadCount) {
            this.unreadCount = unreadCount;
        }
    }

    /** commentDeleted 批查结果行（MS01 内部装配用，不出参；MyBatis 映射需要 public 字段 + 无参构造） */
    public static class CommentDeletedRow {
        public long commentId;
        public int deleted;

        public CommentDeletedRow() {}
    }
}
