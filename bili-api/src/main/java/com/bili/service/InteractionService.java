package com.bili.service;

import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.dto.InteractDtos;
import com.bili.dto.VideoDtos;
import com.bili.entity.CoinFlow;
import com.bili.entity.Favorite;
import com.bili.entity.FavoriteFolder;
import com.bili.entity.Message;
import com.bili.entity.User;
import com.bili.entity.Video;
import com.bili.entity.VideoLike;
import com.bili.mapper.CoinFlowMapper;
import com.bili.mapper.FavoriteMapper;
import com.bili.mapper.UserMapper;
import com.bili.mapper.VideoLikeMapper;
import com.bili.mapper.VideoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 互动服务（FR-IN06~IN10，Phase 3，ADR-18；Phase 4 加点赞消息埋点）。
 * 幂等切换范式（ADR-06 三层防线平移）：uk_user_video 唯一索引兜底 + 行存在性判断 + deleted 状态位切换；
 * 计数一律原子 SQL（减法 GREATEST 防负）；多语句写 @Transactional。
 */
@Service
public class InteractionService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VideoMapper videoMapper;
    private final VideoLikeMapper videoLikeMapper;
    private final FavoriteMapper favoriteMapper;
    private final CoinFlowMapper coinFlowMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final MessageService messageService;
    private final FavoriteFolderService folderService;

    public InteractionService(VideoMapper videoMapper, VideoLikeMapper videoLikeMapper,
                              FavoriteMapper favoriteMapper, CoinFlowMapper coinFlowMapper,
                              UserMapper userMapper, UserService userService, MessageService messageService,
                              FavoriteFolderService folderService) {
        this.videoMapper = videoMapper;
        this.videoLikeMapper = videoLikeMapper;
        this.favoriteMapper = favoriteMapper;
        this.coinFlowMapper = coinFlowMapper;
        this.userMapper = userMapper;
        this.userService = userService;
        this.messageService = messageService;
        this.folderService = folderService;
    }

    /** API-IN01 点赞切换（幂等，ADR-18）：行有效→取消（置 1 + 计数防负减）；无行/已取消→点赞（插/复活 + 计数加）。
     * Phase 4 起仅「未赞→已赞」方向同事务埋点「收到的赞」通知 UP 主（FR-MS01，ADR-19；取消方向不通知）。 */
    @Transactional
    public InteractDtos.LikeResp toggleLike(long videoId, long uid) {
        Video video = requireVideo(videoId);
        VideoLike row = videoLikeMapper.selectRow(uid, videoId);
        boolean liked;
        if (row == null) {
            VideoLike insert = new VideoLike();
            insert.setUserId(uid);
            insert.setVideoId(videoId);
            videoLikeMapper.insert(insert); // uk_user_video 兜底防双行
            videoMapper.incrementLikeCount(videoId);
            liked = true;
            messageService.notify(video.getUserId(), uid, Message.TYPE_LIKE, videoId, null, null);
        } else if (row.getDeleted() == 0) {
            videoLikeMapper.updateState(uid, videoId, 1); // 取消
            videoMapper.decrementLikeCount(videoId); // GREATEST 防负
            liked = false; // 取消方向不产生消息（幂等条件②）
        } else {
            videoLikeMapper.updateState(uid, videoId, 0); // 复活
            videoMapper.incrementLikeCount(videoId);
            liked = true;
            messageService.notify(video.getUserId(), uid, Message.TYPE_LIKE, videoId, null, null);
        }
        Video after = videoMapper.selectById(videoId);
        return new InteractDtos.LikeResp(liked, after.getLikeCount());
    }

    /** API-IN02 互动状态聚合（公开可选 token 视角）：游客三状态恒 false、coinedCount 恒 0 */
    public InteractDtos.InteractionStatus getStatus(long videoId, Long viewerUid) {
        Video video = requireVideo(videoId);
        InteractDtos.InteractionStatus s = new InteractDtos.InteractionStatus();
        s.likeCount = video.getLikeCount();
        s.coinCount = video.getCoinCount();
        s.collectCount = video.getCollectCount();
        if (viewerUid != null) {
            VideoLike likeRow = videoLikeMapper.selectRow(viewerUid, videoId);
            s.liked = likeRow != null && likeRow.getDeleted() == 0;
            Favorite favRow = favoriteMapper.selectRow(viewerUid, videoId);
            s.favorited = favRow != null && favRow.getDeleted() == 0;
            s.folderId = s.favorited && favRow.getFolderId() != null ? favRow.getFolderId() : null; // IN02 增量字段
            int coined = coinFlowMapper.sumCoinedForVideo(viewerUid, videoId);
            s.coined = coined > 0;
            s.coinedCount = coined;
        }
        return s;
    }

    /**
     * API-IN03 投币（登录，ADR-18）。同一 @Transactional 三步：
     * 校验 count/已投上限 → 原子条件扣减（WHERE coin >= n，0 行=40905）→ 插流水 + coin_count 原子 +n。
     * 校验顺序（05 API-IN03 备注）：40403 → 40002 → 40907 → 40905。
     */
    @Transactional
    public InteractDtos.CoinResp coin(long videoId, long uid, InteractDtos.CoinReq req) {
        Video video = requireVideo(videoId);
        int count = req.getCount() == null ? 0 : req.getCount();
        if (count != 1 && count != 2) {
            throw new BusinessException(ErrorCode.CONTENT_INVALID, "投币数量不合法");
        }
        int coined = coinFlowMapper.sumCoinedForVideo(uid, videoId);
        // 每视频累计上限 2 枚（AC-4：投 1 后可再投 1 至累计 2；已投 1 时投 2 = 累计 3 → 40907）
        if (coined + count > 2) {
            throw new BusinessException(ErrorCode.COIN_LIMIT_EXCEEDED); // 40907
        }
        // 原子条件扣减：影响行数 0 = 余额不足（防并发透支，禁止读改写）
        if (userMapper.decreaseCoin(uid, count) == 0) {
            throw new BusinessException(ErrorCode.COIN_NOT_ENOUGH); // 40905
        }
        CoinFlow flow = new CoinFlow();
        flow.setUserId(uid);
        flow.setChangeAmount(-count);
        flow.setBizType(3); // 投币支出
        flow.setBizId(videoId);
        coinFlowMapper.insert(flow);
        videoMapper.incrementCoinCount(videoId, count);
        User after = userMapper.selectById(uid);
        Video afterVideo = videoMapper.selectById(videoId);
        return new InteractDtos.CoinResp(after.getCoin(), afterVideo.getCoinCount(), coined + count);
    }

    /**
     * API-IN04 收藏切换（幂等，与 toggleLike 同范式；Phase 4 四分支语义，ADR-20）。
     * 不带 folderId = Phase 3 切换语义原样（未收藏→入默认夹；已收藏→整体取消；重激活归默认夹防残留旧夹 id）；
     * 带 folderId = 未收藏→收藏入该夹 / 已收藏→移入该夹（同夹幂等 no-op，collect_count 不变）。
     */
    @Transactional
    public InteractDtos.FavoriteResp toggleFavorite(long videoId, long uid, Long folderId) {
        requireVideo(videoId);
        if (folderId == null) {
            // —— 旧契约：不带 folderId（弹层取消勾选当前夹即此形态，整体取消）——
            Favorite row = favoriteMapper.selectRow(uid, videoId);
            boolean favorited;
            if (row == null) {
                Favorite insert = new Favorite();
                insert.setUserId(uid);
                insert.setVideoId(videoId);
                insert.setFolderId(folderService.requireDefaultFolderId(uid)); // 入默认夹
                favoriteMapper.insert(insert);
                videoMapper.incrementCollectCount(videoId);
                favorited = true;
            } else if (row.getDeleted() == 0) {
                favoriteMapper.updateState(uid, videoId, 1); // 整体取消（folder_id 保留，重激活时重置）
                videoMapper.decrementCollectCount(videoId);
                favorited = false;
            } else {
                favoriteMapper.updateStateWithFolder(uid, videoId, 0, folderService.requireDefaultFolderId(uid));
                videoMapper.incrementCollectCount(videoId);
                favorited = true;
            }
            Video after = videoMapper.selectById(videoId);
            return new InteractDtos.FavoriteResp(favorited, after.getCollectCount());
        }
        // —— 新契约：带 folderId（选夹收藏 / 移夹）——
        if (folderId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "收藏夹 ID 不合法");
        }
        FavoriteFolder folder = folderService.requireOwnedFolder(uid, folderId); // 非本人夹/不存在 40405
        Favorite row = favoriteMapper.selectRow(uid, videoId);
        if (row == null) {
            Favorite insert = new Favorite();
            insert.setUserId(uid);
            insert.setVideoId(videoId);
            insert.setFolderId(folder.getId()); // 收藏入该夹
            favoriteMapper.insert(insert);
            videoMapper.incrementCollectCount(videoId);
        } else if (row.getDeleted() == 0) {
            favoriteMapper.moveFolder(uid, videoId, folder.getId()); // 移入该夹（同夹幂等 no-op）；计数不变
        } else {
            favoriteMapper.updateStateWithFolder(uid, videoId, 0, folder.getId()); // 重激活直接归该夹
            videoMapper.incrementCollectCount(videoId);
        }
        Video after = videoMapper.selectById(videoId);
        return new InteractDtos.FavoriteResp(true, after.getCollectCount());
    }

    /**
     * API-IN05 空间收藏列表（公开分页；show_favorites 服务端过滤，FR-PF05 承诺兑现——VD04 同口径）：
     * 本人视角全量；他人/游客且开关关 → 空列表（total=0，不报错不暴露）。
     */
    public VideoDtos.VideoPageResp listFavorites(long targetId, int page, int size, Long viewerUid) {
        User target = userService.requireUser(targetId); // 40401
        boolean self = viewerUid != null && viewerUid == targetId;
        boolean visible = self || target.getShowFavorites() == null || target.getShowFavorites() == 1;
        if (!visible) {
            return new VideoDtos.VideoPageResp(0, page, size, List.of());
        }
        long total = favoriteMapper.countFavoriteVideos(targetId);
        List<VideoDtos.VideoItem> list = total == 0
                ? List.of()
                : favoriteMapper.selectFavoriteVideos(targetId, (page - 1) * size, size);
        formatDates(list);
        return new VideoDtos.VideoPageResp(total, page, size, list);
    }

    /** API-IN06 空间点赞列表（公开分页；show_likes 服务端过滤，同 listFavorites 范式） */
    public VideoDtos.VideoPageResp listLikes(long targetId, int page, int size, Long viewerUid) {
        User target = userService.requireUser(targetId);
        boolean self = viewerUid != null && viewerUid == targetId;
        boolean visible = self || target.getShowLikes() == null || target.getShowLikes() == 1;
        if (!visible) {
            return new VideoDtos.VideoPageResp(0, page, size, List.of());
        }
        long total = videoLikeMapper.countLikedVideos(targetId);
        List<VideoDtos.VideoItem> list = total == 0
                ? List.of()
                : videoLikeMapper.selectLikedVideos(targetId, (page - 1) * size, size);
        formatDates(list);
        return new VideoDtos.VideoPageResp(total, page, size, list);
    }

    /**
     * API-IN11 夹内容列表（仅本人，收藏管理页专用；分页收藏时间倒序，条目范式同 IN05）。
     * 非本人夹/不存在统一 40405（不暴露存在性）；已逻辑删除视频过滤与 IN05 同口径。
     */
    public VideoDtos.VideoPageResp listFolderFavorites(long uid, long folderId, int page, int size) {
        folderService.requireOwnedFolder(uid, folderId); // 40405
        long total = favoriteMapper.countFolderVideos(uid, folderId);
        List<VideoDtos.VideoItem> list = total == 0
                ? List.of()
                : favoriteMapper.selectFolderVideos(uid, folderId, (page - 1) * size, size);
        formatDates(list);
        return new VideoDtos.VideoPageResp(total, page, size, list);
    }

    private Video requireVideo(long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        return video;
    }

    /** createdAtRaw → createdAt（yyyy-MM-dd HH:mm:ss），与 VideoService 同款 */
    private void formatDates(List<VideoDtos.VideoItem> list) {
        for (VideoDtos.VideoItem item : list) {
            item.createdAt = item.createdAtRaw == null ? null : DT.format(item.createdAtRaw);
            item.createdAtRaw = null;
        }
    }
}
