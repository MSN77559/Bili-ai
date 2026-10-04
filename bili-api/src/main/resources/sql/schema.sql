-- TB-01 用户表（04-数据模型设计 §3，v1.5 默认背景图模式）
CREATE TABLE IF NOT EXISTS `user` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `email`         VARCHAR(254)    NOT NULL COMMENT '邮箱，登录唯一凭证，统一小写存储，格式校验见 FR-AU01',
  `password_hash` VARCHAR(60)     NOT NULL COMMENT 'BCrypt散列，永不出现在任何响应中',
  `nickname`      VARCHAR(16)     NOT NULL COMMENT '昵称，允许重复，2-16字符',
  `avatar_url`    VARCHAR(255)    NOT NULL DEFAULT '/api/files/avatars/default.png' COMMENT '头像访问路径（相对路径）',
  `banner_url`    VARCHAR(255)    NOT NULL DEFAULT '/api/files/banners/default.png' COMMENT '空间主页背景图相对路径（默认 default.png，FR-PF07）',
  `gender`        TINYINT         NOT NULL DEFAULT 0 COMMENT '性别：0保密 1男 2女',
  `birthday`      DATE            NULL COMMENT '生日，可空，不晚于当天',
  `sign`          VARCHAR(100)    NOT NULL DEFAULT '' COMMENT '个人签名，0-100字符',
  `show_contribute` TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '空间展示：投稿tab对他人可见（FR-PF05）1公开 0隐藏',
  `show_favorites`  TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '空间展示：收藏夹tab对他人可见（FR-PF05）',
  `show_likes`      TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '空间展示：点赞tab对他人可见（FR-PF05）',
  `show_birthday`   TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '生日对他人可见（FR-PF06）1公开 0隐藏',
  `show_follows`    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '关注/粉丝列表对他人可见（FR-FO06，Phase 4）1公开 0隐藏（单开关管两侧，本人恒可见）',
  `coin`          INT            NOT NULL DEFAULT 0 COMMENT '硬币余额（FR-PF08），初始 0，获取/消费功能 Phase 3',
  `deleted`       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0正常 1已注销（本期不暴露注销接口）',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '资料最后修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- TB-02 关注关系表（04-数据模型设计 §3）
CREATE TABLE IF NOT EXISTS `follow` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `follower_id`  BIGINT UNSIGNED NOT NULL COMMENT '关注发起方（粉丝）',
  `following_id` BIGINT UNSIGNED NOT NULL COMMENT '被关注方',
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_following` (`follower_id`, `following_id`),
  KEY `idx_following_follower` (`following_id`, `follower_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关注关系表';

-- TB-03 拉黑关系表（04-数据模型设计 §3，v1.3 新增，ADR-15）
CREATE TABLE IF NOT EXISTS `block` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `blocker_id`  BIGINT UNSIGNED NOT NULL COMMENT '拉黑发起方',
  `blocked_id`  BIGINT UNSIGNED NOT NULL COMMENT '被拉黑方',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '拉黑时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_blocker_blocked` (`blocker_id`, `blocked_id`),
  KEY `idx_blocked_blocker` (`blocked_id`, `blocker_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拉黑关系表';

-- TB-04 视频表（04-数据模型设计 §3，v1.7 新增，Phase 2 / ADR-17）
CREATE TABLE IF NOT EXISTS `video` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '视频ID',
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT 'UP主用户ID（无物理外键，ADR-11）',
  `title`       VARCHAR(80)     NOT NULL COMMENT '标题，2-80字符（FR-VD01）',
  `category`    TINYINT UNSIGNED NOT NULL COMMENT '分区：1~14（ADR-10 清单顺序，映射 SSOT 在前端 CATEGORIES 常量）',
  `description` VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '简介，0-500字符，空串而非 NULL',
  `video_url`   VARCHAR(255)    NOT NULL COMMENT '视频文件相对路径（/api/files/videos/xxx.mp4）',
  `cover_url`   VARCHAR(255)    NULL DEFAULT NULL COMMENT '封面图相对路径；NULL=未上传，前端 GRADIENTS 渐变兜底（ADR-17）',
  `duration`    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '时长（秒），1~86400，投稿时前端 loadedmetadata 读取（ADR-17）',
  `view_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '播放数（冗余计数，ADR-17；详情接口原子自增）',
  `like_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '点赞数（冗余计数，ADR-17/18；FR-IN06 写路径接管）',
  `coin_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '投币数（冗余计数，ADR-18；FR-IN07 投币原子 +n）',
  `collect_count` INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '收藏数（冗余计数，ADR-18；FR-IN08 切换原子 ±1）',
  `deleted`     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除预留（本期无删除入口，ADR-11）',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投稿时间（列表排序口径：倒序）',
  `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_category_created_at` (`category`, `created_at`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频表';

-- TB-05 弹幕表（04-数据模型设计 §3，v1.8 新增，Phase 3 / FR-IN01/IN02）
CREATE TABLE IF NOT EXISTS `danmaku` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '弹幕ID',
  `video_id`    BIGINT UNSIGNED NOT NULL COMMENT '视频ID（无物理外键，ADR-11）',
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '发送者用户ID（token uid）',
  `content`     VARCHAR(100)    NOT NULL COMMENT '弹幕内容，1-100字符（码点计，FR-IN01）',
  `time_offset` INT UNSIGNED    NOT NULL COMMENT '出现时间偏移（毫秒），0~视频时长毫秒',
  `mode`        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '弹幕类型：1滚动 4底部 5顶部（预留，本期恒1，前端只实现滚动）',
  `deleted`     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除（ADR-11，本期无删除入口预留）',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_video_offset` (`video_id`, `time_offset`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='弹幕表';

-- TB-06 评论表（04-数据模型设计 §3，v1.8 新增，Phase 3 / FR-IN03~IN05，两级楼中楼）
CREATE TABLE IF NOT EXISTS `comment` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `video_id`          BIGINT UNSIGNED NOT NULL COMMENT '视频ID（无物理外键，ADR-11）',
  `user_id`           BIGINT UNSIGNED NOT NULL COMMENT '评论者用户ID（token uid）',
  `parent_id`         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父评论ID：0=父评论，非0=该父评论的楼中楼回复（两级结构，FR-IN03）',
  `reply_to_user_id`  BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '回复对象用户ID（楼中楼内回复某条回复时记录；NULL=直接回复父评论）',
  `content`           VARCHAR(500)    NOT NULL COMMENT '评论内容，1-500字符（码点计，FR-IN03）',
  `like_count`        INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '评论点赞数（预留水位，本期不接写路径，ADR-17 同思路）',
  `deleted`           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删（父评论删除时回复级联置1，ADR-11）',
  `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间（列表排序口径：父评论倒序/回复正序）',
  PRIMARY KEY (`id`),
  KEY `idx_video_parent` (`video_id`, `parent_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论表（两级楼中楼）';

-- TB-07 视频点赞表（04-数据模型设计 §3，v1.8 新增，Phase 3 / FR-IN06，ADR-18）
CREATE TABLE IF NOT EXISTS `video_like` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '点赞者',
  `video_id`   BIGINT UNSIGNED NOT NULL COMMENT '视频ID',
  `deleted`    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '0有效 1已取消（幂等切换状态位，ADR-18）',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_video` (`user_id`, `video_id`),
  KEY `idx_video_id` (`video_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频点赞表（只对视频，评论点赞不做）';

-- TB-08 硬币流水表（04-数据模型设计 §3，v1.8 新增，Phase 3 / FR-IN07/IN11，ADR-18）
CREATE TABLE IF NOT EXISTS `coin_flow` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `change_amount` INT             NOT NULL COMMENT '变动枚数（正=收入，负=支出）',
  `biz_type`      TINYINT UNSIGNED NOT NULL COMMENT '业务类型：1签到 2注册赠送 3投币支出',
  `biz_id`        BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '业务关联ID（投币=videoId；签到/注册赠送=NULL）',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流水时间（签到防重按当日 biz_type=1 判断）',
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_user_biz_date` (`user_id`, `biz_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='硬币流水表（余额单一事实在 user.coin，本表仅审计与展示）';

-- TB-09 收藏表（04-数据模型设计 §3，v1.8 新增，Phase 3 / FR-IN08/IN09，ADR-18）
CREATE TABLE IF NOT EXISTS `favorite` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '收藏者',
  `video_id`   BIGINT UNSIGNED NOT NULL COMMENT '视频ID',
  `folder_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所属收藏夹（Phase 4，单夹归属；0=待回填悬挂值，业务上不出现——存量回填后恒为真实夹id）',
  `deleted`    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '0有效 1已取消（幂等切换状态位，同 video_like）',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间（IN05 列表排序口径）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_video` (`user_id`, `video_id`),
  KEY `idx_video_id` (`video_id`, `deleted`),
  KEY `idx_folder_user` (`folder_id`, `user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏表（Phase 4 起多收藏夹单夹归属，见 TB-12）';

-- TB-10 观看历史表（04-数据模型设计 §3，v1.9 新增，Phase 4 / FR-HI01~HI03，ADR-19）
CREATE TABLE IF NOT EXISTS `history` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '观看者',
  `video_id`   BIGINT UNSIGNED NOT NULL COMMENT '视频ID',
  `viewed_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后观看时间（重看即更新置顶；HI01 排序口径）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_video` (`user_id`, `video_id`),
  KEY `idx_user_viewed` (`user_id`, `viewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='观看历史表（物理删除，无状态位——重新观看自然复活，ADR-19）';

-- TB-11 消息通知表（04-数据模型设计 §3，v1.9 新增，Phase 4 / FR-MS01~MS03，ADR-19）
CREATE TABLE IF NOT EXISTS `message` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `receiver_id` BIGINT UNSIGNED NOT NULL COMMENT '接收者',
  `sender_id`   BIGINT UNSIGNED NOT NULL COMMENT '发送者（昵称头像 JOIN user 实时取）',
  `type`        TINYINT         NOT NULL COMMENT '消息类型：1回复我的 2收到的赞 3新粉丝',
  `video_id`    BIGINT UNSIGNED NULL COMMENT '视频引用（type=1/2 必填，type=3 为 NULL）',
  `comment_id`  BIGINT UNSIGNED NULL COMMENT '评论引用（仅 type=1；评论删除不删本行，列表按 deleted 置灰）',
  `content`     VARCHAR(200)    NULL COMMENT '内容快照（仅 type=1：回复内容前 200 字；type=2/3 为 NULL）',
  `is_read`     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息时间（MS01 排序口径）',
  PRIMARY KEY (`id`),
  KEY `idx_receiver_read` (`receiver_id`, `is_read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息通知表（三类埋点产生；引用+快照混合，ADR-19）';

-- TB-12 收藏夹表（04-数据模型设计 §3，v1.9 新增，Phase 4 / FR-IN12~IN16，ADR-20）
CREATE TABLE IF NOT EXISTS `favorite_folder` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键（folderId）',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
  `name`       VARCHAR(20)     NOT NULL COMMENT '收藏夹名称，1-20字符；uk(user_id, name) 防同名（DuplicateKey→40909）',
  `is_default` TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1默认收藏夹（注册即建、不可删不可改名 40908；删普通夹的兜底容器）',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '改名时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_name` (`user_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏夹表（Phase 4，ADR-20）';
