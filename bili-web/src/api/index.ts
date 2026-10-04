// 按模块封装的接口调用（03 §3）：契约对应 05
import { api } from '@/utils/request'
import type { LoginResp, MyProfile, PublicProfile, FollowResp, BlockResp, ResetTokenResp, AvatarResp, BannerResp, FollowPageResp, VideoPageResp, VideoDetail, PublishResp, DanmakuListResp, SendDanmakuResp, CommentItem, CommentPageResp, ReplyPageResp, LikeResp, InteractionState, CoinResp, FavoriteResp, CheckInResp, CoinFlowPageResp, SearchUserPageResp, HistoryPageResp, MessagePageResp, UnreadCountResp, FolderListResp, FolderCreateResp } from '@/types/api'

// ---- auth（API-AU01~AU06）----
export const register = (email: string, password: string) =>
  api<LoginResp>({ method: 'post', url: '/auth/register', data: { email, password } })

export const login = (email: string, password: string) =>
  api<LoginResp>({ method: 'post', url: '/auth/login', data: { email, password } })

export const logout = () => api<null>({ method: 'post', url: '/auth/logout' })

export const sendResetCode = (email: string) =>
  api<null>({ method: 'post', url: '/auth/password/reset-code', data: { email } })

export const verifyResetCode = (email: string, code: string) =>
  api<ResetTokenResp>({ method: 'post', url: '/auth/password/verify-code', data: { email, code } })

export const resetPassword = (resetToken: string, newPassword: string) =>
  api<null>({ method: 'post', url: '/auth/password/reset', data: { resetToken, newPassword } })

// ---- users（API-US01~US04）----
export const getMyProfile = () => api<MyProfile>({ method: 'get', url: '/users/me' })

export const getPublicProfile = (userId: number | string) =>
  api<PublicProfile>({ method: 'get', url: `/users/${userId}` })

export const updateMyProfile = (data: {
  nickname: string
  gender: number
  birthday: string | null
  sign: string
  /** 空间展示开关（FR-PF05）/生日公开开关（FR-PF06）/关注列表开关（FR-FO06）：可选，不传=不修改 */
  showContribute?: number
  showFavorites?: number
  showLikes?: number
  showBirthday?: number
  showFollows?: number
}) =>
  api<null>({ method: 'put', url: '/users/me', data })

export const uploadAvatar = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return api<AvatarResp>({ method: 'post', url: '/users/me/avatar', data: form })
}

export const uploadBanner = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return api<BannerResp>({ method: 'post', url: '/users/me/banner', data: form })
}

// ---- follow（API-FO01/02）----
export const followUser = (userId: number) =>
  api<FollowResp>({ method: 'post', url: `/users/${userId}/follow` })

export const unfollowUser = (userId: number) =>
  api<FollowResp>({ method: 'delete', url: `/users/${userId}/follow` })

// ---- follow 列表（API-FO03/FO04，公开分页，FR-FO05）----
export const getFollowings = (userId: number, page = 1, size = 20) =>
  api<FollowPageResp>({ method: 'get', url: `/users/${userId}/followings`, params: { page, size } })

export const getFollowers = (userId: number, page = 1, size = 20) =>
  api<FollowPageResp>({ method: 'get', url: `/users/${userId}/followers`, params: { page, size } })

// ---- block（API-BK01/02，ADR-15）----
export const blockUser = (userId: number) =>
  api<BlockResp>({ method: 'post', url: `/users/${userId}/block` })

export const unblockUser = (userId: number) =>
  api<BlockResp>({ method: 'delete', url: `/users/${userId}/block` })

// ---- video（API-VD01~VD04，Phase 2）----

/** 投稿（multipart：file + cover? + title/category/description/duration）；timeout:0 覆盖实例默认 10s（大文件上传） */
export const publishVideo = (form: FormData) =>
  api<PublishResp>({ method: 'post', url: '/videos', data: form, timeout: 0 })

/** 全站/分区视频列表（公开；缺省全站最新） */
export const getVideos = (params: { page: number; size: number; category?: number }) =>
  api<VideoPageResp>({ method: 'get', url: '/videos', params })

/** 视频详情（公开可选 token 视角；接口顺带 view_count+1，ADR-17） */
export const getVideoDetail = (videoId: number) =>
  api<VideoDetail>({ method: 'get', url: `/videos/${videoId}` })

/** 空间投稿列表（公开；show_contribute 服务端过滤：他人/游客且开关关 → 空列表） */
export const getUserVideos = (userId: number, page = 1, size = 20) =>
  api<VideoPageResp>({ method: 'get', url: `/users/${userId}/videos`, params: { page, size } })

// ---- danmaku（API-DL01/DL02，Phase 3）----

/** 发弹幕（登录；timeOffset=发送瞬间视频 currentTime 毫秒，成功后前端立即上屏） */
export const sendDanmaku = (videoId: number, content: string, timeOffset: number) =>
  api<SendDanmakuResp>({ method: 'post', url: `/videos/${videoId}/danmaku`, data: { content, timeOffset } })

/** 弹幕列表（公开全量，按 timeOffset 升序，无分页） */
export const getDanmakuList = (videoId: number) =>
  api<DanmakuListResp>({ method: 'get', url: `/videos/${videoId}/danmaku` })

// ---- comment（API-CM01~CM03，Phase 3）----

/** 发评论/回复（登录；parentId=0 父评论、非 0 楼中楼回复；返回新评论完整条目） */
export const sendComment = (videoId: number, content: string, parentId = 0, replyToUserId?: number) =>
  api<CommentItem>({ method: 'post', url: `/videos/${videoId}/comments`, data: { content, parentId, replyToUserId } })

/** 评论列表（公开；不传 parent=父评论分页倒序 + 首屏回复聚合） */
export const getComments = (videoId: number, page = 1, size = 20) =>
  api<CommentPageResp>({ method: 'get', url: `/videos/${videoId}/comments`, params: { page, size } })

/** 回复分页（公开；楼中楼展开时传 parent=父评论 id，时间正序） */
export const getReplies = (videoId: number, parentId: number, page = 1, size = 20) =>
  api<ReplyPageResp>({ method: 'get', url: `/videos/${videoId}/comments`, params: { page, size, parent: parentId } })

/** 删除评论（登录仅本人；父评论级联逻辑删回复） */
export const deleteComment = (commentId: number) =>
  api<null>({ method: 'delete', url: `/comments/${commentId}` })

// ---- interaction（API-IN01~IN06，Phase 3）----

/** 点赞切换（登录幂等：liked=本次请求后状态，响应校准本地） */
export const toggleLike = (videoId: number) =>
  api<LikeResp>({ method: 'post', url: `/videos/${videoId}/like` })

/** 互动状态聚合（公开可选 token：三状态+三计数+已投枚数，播放页初始化一次拉齐） */
export const getInteractions = (videoId: number) =>
  api<InteractionState>({ method: 'get', url: `/videos/${videoId}/interactions` })

/** 投币（登录；count 仅 1/2，每视频累计 2 枚封顶；返回投后余额与最新计数） */
export const coinVideo = (videoId: number, count: number) =>
  api<CoinResp>({ method: 'post', url: `/videos/${videoId}/coin`, data: { count } })

/** 收藏切换（登录幂等；Phase 4 起可选 folderId——不传=整体切换语义（收藏入默认夹/取消），
 *  传=选夹收藏（未收藏→入该夹）/移夹（已收藏→移入该夹，同夹 no-op）；取消勾选当前夹即不传调用） */
export const toggleFavorite = (videoId: number, folderId?: number) =>
  api<FavoriteResp>({ method: 'post', url: `/videos/${videoId}/favorite`, data: folderId ? { folderId } : {} })

/** 空间收藏列表（公开分页；show_favorites 服务端过滤：他人/游客且开关关 → 空列表） */
export const getUserFavorites = (userId: number, page = 1, size = 20) =>
  api<VideoPageResp>({ method: 'get', url: `/users/${userId}/favorites`, params: { page, size } })

/** 空间点赞列表（公开分页；show_likes 服务端过滤同上） */
export const getUserLikes = (userId: number, page = 1, size = 20) =>
  api<VideoPageResp>({ method: 'get', url: `/users/${userId}/likes`, params: { page, size } })

// ---- auth 签到 / users 流水（API-AU07/US06，Phase 3）----

/** 每日签到（登录；+5 枚，当日防重 40906；返回签到后余额） */
export const checkIn = () =>
  api<CheckInResp>({ method: 'post', url: '/auth/check-in' })

/** 我的硬币流水（登录仅本人；分页倒序；「当日已签」由首页是否存在今日 bizType=1 推导） */
export const getMyCoinFlows = (page = 1, size = 20) =>
  api<CoinFlowPageResp>({ method: 'get', url: '/users/me/coin-flows', params: { page, size } })

// ---- search（API-SE01/SE02，Phase 4 公开白名单）----

/** 视频搜索（公开；标题 LIKE 模糊匹配，条目复用 VD02 卡片范式含 UP 摘要） */
export const searchVideos = (keyword: string, page = 1, size = 20) =>
  api<VideoPageResp>({ method: 'get', url: '/search/videos', params: { keyword, page, size } })

/** 用户搜索（公开可选 token 视角；昵称 LIKE 模糊匹配，条目复用 FO03 范式含 isFollowed） */
export const searchUsers = (keyword: string, page = 1, size = 20) =>
  api<SearchUserPageResp>({ method: 'get', url: '/search/users', params: { keyword, page, size } })

// ---- history（API-HI01~03，Phase 4 仅本人）----

/** 我的观看历史（登录仅本人；viewed_at 倒序；视频被删的历史行不返回） */
export const getMyHistory = (page = 1, size = 20) =>
  api<HistoryPageResp>({ method: 'get', url: '/users/me/history', params: { page, size } })

/** 删除单条历史（登录；幂等——不存在也 200） */
export const deleteHistory = (videoId: number) =>
  api<null>({ method: 'delete', url: `/users/me/history/${videoId}` })

/** 清空历史（登录；幂等——无历史时 200） */
export const clearHistory = () =>
  api<null>({ method: 'delete', url: '/users/me/history' })

// ---- message（API-MS01~04，Phase 4 仅本人）----

/** 消息列表（登录仅本人；type 可选筛选 1回复我的/2收到的赞/3新粉丝，缺省全部；分页倒序） */
export const getMyMessages = (params: { type?: 1 | 2 | 3; page?: number; size?: number } = {}) =>
  api<MessagePageResp>({ method: 'get', url: '/users/me/messages', params })

/** 标记单条消息已读（登录；幂等；不存在/非本人统一 40406 防存在性泄露） */
export const markMessageRead = (id: number) =>
  api<null>({ method: 'post', url: `/users/me/messages/${id}/read` })

/** 全部消息已读（登录；幂等） */
export const markAllMessagesRead = () =>
  api<null>({ method: 'post', url: '/users/me/messages/read-all' })

/** 未读消息数（登录；实时 COUNT 轻量端点——NavBar 红点专用） */
export const getUnreadCount = () =>
  api<UnreadCountResp>({ method: 'get', url: '/users/me/messages/unread-count' })

// ---- favorite folder（API-IN07~IN11，Phase 4 仅本人）----

/** 我的收藏夹列表（登录；非分页全量；默认夹恒首位） */
export const getMyFolders = () =>
  api<FolderListResp>({ method: 'get', url: '/users/me/favorite-folders' })

/** 创建收藏夹（登录；name trim 后 1~20 字，同名 40909） */
export const createFolder = (name: string) =>
  api<FolderCreateResp>({ method: 'post', url: '/users/me/favorite-folders', data: { name } })

/** 重命名收藏夹（登录；默认夹 40908、同名 40909、非本人夹 40405） */
export const renameFolder = (folderId: number, name: string) =>
  api<null>({ method: 'put', url: `/users/me/favorite-folders/${folderId}`, data: { name } })

/** 删除收藏夹（登录；默认夹 40908；夹内条目同事务移入默认夹，collect_count 不变） */
export const deleteFolder = (folderId: number) =>
  api<null>({ method: 'delete', url: `/users/me/favorite-folders/${folderId}` })

/** 夹内容列表（登录仅本人；分页收藏时间倒序；条目复用 IN05 卡片范式） */
export const getFolderFavorites = (folderId: number, page = 1, size = 20) =>
  api<VideoPageResp>({ method: 'get', url: `/users/me/favorite-folders/${folderId}/favorites`, params: { page, size } })
