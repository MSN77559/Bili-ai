// 与 05-API接口设计 的 DTO 一一对应（03 §3 约定）

/** 05 §2.1 统一响应 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 登录/注册响应（API-AU01/AU02） */
export interface LoginResp {
  token: string
  expiresIn: number
  user: { userId: number; nickname: string; avatarUrl: string }
}

/** 当前用户完整资料（API-US01，唯一含 email） */
export interface MyProfile {
  userId: number
  email: string
  nickname: string
  avatarUrl: string
  /** 空间背景图（FR-PF07）：未上传时为默认图 default.png（DB 列默认值，与 avatarUrl 同构） */
  bannerUrl: string
  gender: number
  birthday: string | null
  sign: string
  showContribute: boolean
  showFavorites: boolean
  showLikes: boolean
  showBirthday: boolean
  /** 关注/粉丝列表公开开关（FR-FO06，Phase 4，编辑页回显用） */
  showFollows: boolean
  /** 硬币余额（FR-PF08）：本期恒 0（获取/消费 Phase 3） */
  coin: number
  createdAt: string
}

/** 公开主页信息（API-US02，不含 email/createdAt） */
export interface PublicProfile {
  userId: number
  nickname: string
  avatarUrl: string
  /** 空间背景图（FR-PF07）：未上传时为默认图 default.png；对所有访客公开 */
  bannerUrl: string
  gender: number
  /** 主人关闭生日公开（FR-PF06）且访问者非本人时为 null */
  birthday: string | null
  sign: string
  followingCount: number
  followerCount: number
  isSelf: boolean
  isFollowed: boolean
  /** 当前访问者是否已拉黑 TA（FR-BK01，单向视角） */
  isBlocked: boolean
  showContribute: boolean
  showFavorites: boolean
  showLikes: boolean
}

/** 关注/取关响应（API-FO01/02） */
export interface FollowResp {
  isFollowed: boolean
  followerCount: number
}

/** 拉黑/取消拉黑响应（API-BK01/02） */
export interface BlockResp {
  isBlocked: boolean
  followerCount: number
}

/** 忘记密码第二步响应（API-AU05） */
export interface ResetTokenResp {
  resetToken: string
  expiresIn: number
}

/** 头像上传响应（API-US04） */
export interface AvatarResp {
  avatarUrl: string
}

/** 关注/粉丝列表条目（API-FO03/FO04，FR-FO05）；isFollowed 为访问者视角 */
export interface FollowPageItem {
  userId: number
  nickname: string
  avatarUrl: string
  sign: string
  isFollowed: boolean
}

/** 关注/粉丝分页响应（05 §1 分页约定） */
export interface FollowPageResp {
  total: number
  page: number
  size: number
  list: FollowPageItem[]
}

/** 背景图上传响应（API-US05，FR-PF07） */
export interface BannerResp {
  bannerUrl: string
}

/** 视频列表条目（API-VD02/VD04）；upNickname/upAvatarUrl 仅 VD02 返回（VD04 主人页头已知） */
export interface VideoItem {
  videoId: number
  userId: number
  upNickname?: string
  upAvatarUrl?: string
  title: string
  /** 分区 1~14（名称映射 SSOT 在前端 CATEGORIES，index+1 即 id） */
  category: number
  /** null=未上传封面，前端按 videoId 取模走 GRADIENTS 渐变兜底（ADR-17） */
  coverUrl: string | null
  /** 时长秒数（前端格式化 m:ss） */
  duration: number
  viewCount: number
  likeCount: number
  /** yyyy-MM-dd HH:mm:ss */
  createdAt: string
}

/** 视频分页响应（05 §1 分页约定，API-VD02/VD04） */
export interface VideoPageResp {
  total: number
  page: number
  size: number
  list: VideoItem[]
}

/** UP 主摘要（API-VD03，不含 email 等脱敏字段） */
export interface UpSummary {
  userId: number
  nickname: string
  avatarUrl: string
  sign: string
  followerCount: number
}

/** 视频详情（API-VD03；viewCount 为本次自增后的值；游客 isSelf/isFollowed 恒 false） */
export interface VideoDetail {
  videoId: number
  userId: number
  title: string
  category: number
  description: string
  videoUrl: string
  coverUrl: string | null
  duration: number
  viewCount: number
  likeCount: number
  createdAt: string
  up: UpSummary
  isSelf: boolean
  isFollowed: boolean
}

/** 视频投稿响应（API-VD01） */
export interface PublishResp {
  videoId: number
  videoUrl: string
  coverUrl: string | null
}

// ---- Phase 3 互动模块（05 DL/CM/IN/AU07/US06 段）----

/** 弹幕列表条目（API-DL02，公开全量按 timeOffset 升序） */
export interface DanmakuItem {
  danmakuId: number
  content: string
  /** 毫秒偏移 */
  timeOffset: number
  /** 类型（本期恒 1 滚动，列预留） */
  mode: number
  nickname: string
}

/** 弹幕列表响应（无分页，一次拉齐） */
export interface DanmakuListResp {
  total: number
  list: DanmakuItem[]
}

/** 发弹幕响应（API-DL01） */
export interface SendDanmakuResp {
  danmakuId: number
}

/** 评论/回复条目（CM01 返回与 CM02 列表行同构） */
export interface CommentItem {
  commentId: number
  userId: number
  nickname: string
  avatarUrl: string
  content: string
  /** 0=父评论（仅 CM01 返回；CM02 父行不含） */
  parentId?: number
  replyToUserId: number | null
  replyToNickname: string | null
  /** yyyy-MM-dd HH:mm:ss */
  createdAt: string
}

/** 父评论行（CM02 父列表：CommentItem + 回复聚合） */
export interface ParentCommentItem extends CommentItem {
  /** 有效回复数 */
  replyCount: number
  /** 首屏聚合的前 2 条回复（时间正序；replyCount ≤2 时全量）；空为空数组 */
  replies: CommentItem[]
}

/** 父评论分页响应（CM02 不传 parent，时间倒序） */
export interface CommentPageResp {
  /** 父评论总数（不含回复） */
  total: number
  page: number
  size: number
  list: ParentCommentItem[]
}

/** 回复分页响应（CM02 传 parent，时间正序） */
export interface ReplyPageResp {
  total: number
  page: number
  size: number
  list: CommentItem[]
}

/** 点赞切换响应（API-IN01：liked=本次请求后状态） */
export interface LikeResp {
  liked: boolean
  likeCount: number
}

/** 互动状态聚合（API-IN02：播放页初始化一次拉齐；游客三状态恒 false/0） */
export interface InteractionState {
  liked: boolean
  coined: boolean
  favorited: boolean
  likeCount: number
  coinCount: number
  collectCount: number
  /** 本人累计已投枚数（0/1/2；游客恒 0） */
  coinedCount: number
  /** 当前收藏所在夹 id（Phase 4 增量字段；未收藏/游客恒 null——弹层初始化勾选用） */
  folderId: number | null
}

/** 投币响应（API-IN03：投后余额与最新计数） */
export interface CoinResp {
  /** 投后余额 */
  coin: number
  coinCount: number
  coinedCount: number
}

/** 收藏切换响应（API-IN04） */
export interface FavoriteResp {
  favorited: boolean
  collectCount: number
}

/** 每日签到响应（API-AU07） */
export interface CheckInResp {
  /** 签到后余额 */
  coin: number
}

/** 硬币流水条目（API-US06，分页倒序） */
export interface CoinFlowItem {
  flowId: number
  /** 正=收入、负=支出 */
  changeAmount: number
  /** 1 签到 / 2 注册赠送 / 3 投币支出（中文映射 SSOT 在前端） */
  bizType: number
  createdAt: string
}

/** 硬币流水分页响应 */
export interface CoinFlowPageResp {
  total: number
  page: number
  size: number
  list: CoinFlowItem[]
}

// ---- Phase 4 搜索/历史/消息/收藏夹（05 SE/HI/MS/IN07~IN11 段）----

/** 用户搜索条目（API-SE02，复用 FO03 条目范式；isFollowed 访问者视角） */
export interface SearchUserItem {
  userId: number
  nickname: string
  avatarUrl: string
  sign: string
  isFollowed: boolean
}

/** 用户搜索分页响应（API-SE02） */
export interface SearchUserPageResp {
  total: number
  page: number
  size: number
  list: SearchUserItem[]
}

/** 历史条目（API-HI01，JOIN video 摘要；视频被删的历史行不返回） */
export interface HistoryItem {
  videoId: number
  title: string
  /** null=无封面，与 VideoItem.coverUrl 同兜底 */
  coverUrl: string | null
  /** 时长秒数 */
  duration: number
  upUserId: number
  upNickname: string
  /** yyyy-MM-dd HH:mm:ss */
  viewedAt: string
}

/** 历史分页响应（API-HI01，viewed_at 倒序） */
export interface HistoryPageResp {
  total: number
  page: number
  size: number
  list: HistoryItem[]
}

/** 消息条目（API-MS01；type 1回复我的/2收到的赞/3新粉丝） */
export interface MessageItem {
  id: number
  /** 1回复我的 2收到的赞 3新粉丝（文案与跳转由前端按此拼装） */
  type: 1 | 2 | 3
  senderId: number
  /** 发送者实时摘要（JOIN user，改名跟得上） */
  senderNickname: string
  senderAvatarUrl: string
  /** type=1/2 跳转目标 /video/{id}；type=3 为 null */
  videoId: number | null
  /** 仅 type=1 */
  commentId: number | null
  /** 评论内容快照（仅 type=1，前 200 字） */
  content: string | null
  /** 仅 type=1 有意义：被回复评论已删除 → 前端置灰「该评论已删除」（消息不删，ADR-19） */
  commentDeleted: boolean
  /** 未读圆点渲染依据 */
  isRead: boolean
  /** yyyy-MM-dd HH:mm:ss */
  createdAt: string
}

/** 消息分页响应（API-MS01，created_at 倒序） */
export interface MessagePageResp {
  total: number
  page: number
  size: number
  list: MessageItem[]
}

/** 未读消息数响应（API-MS04，NavBar 红点专用轻量端点） */
export interface UnreadCountResp {
  unreadCount: number
}

/** 收藏夹条目（API-IN07/IN08；count 为夹内有效收藏数实时 COUNT） */
export interface FavoriteFolderItem {
  folderId: number
  name: string
  count: number
  /** 默认夹标记（前端禁用删/改按钮；服务端 40908 兜底） */
  isDefault: boolean
  /** yyyy-MM-dd HH:mm:ss */
  createdAt: string
}

/** 夹列表响应（API-IN07，非分页全量；默认夹恒首位） */
export interface FolderListResp {
  list: FavoriteFolderItem[]
}

/** 创建收藏夹响应（API-IN08，新建夹即时空夹） */
export interface FolderCreateResp {
  folderId: number
  name: string
  count: number
  isDefault: boolean
  createdAt: string
}
