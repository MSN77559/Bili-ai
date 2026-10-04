package com.bili.service;

import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.dto.VideoDtos;
import com.bili.entity.User;
import com.bili.entity.Video;
import com.bili.mapper.FollowMapper;
import com.bili.mapper.HistoryMapper;
import com.bili.mapper.UserMapper;
import com.bili.mapper.VideoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 视频服务（FR-VD01~VD05，Phase 2 / ADR-17；Phase 4 加历史埋点）。
 */
@Service
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final UserService userService;
    private final HistoryMapper historyMapper;

    public VideoService(VideoMapper videoMapper, UserMapper userMapper, FollowMapper followMapper,
                        UserService userService, HistoryMapper historyMapper) {
        this.videoMapper = videoMapper;
        this.userMapper = userMapper;
        this.followMapper = followMapper;
        this.userService = userService;
        this.historyMapper = historyMapper;
    }

    /** API-VD02 全站视频列表（分页 + 可选 category 筛选，公开）。page/size/category 已由 Controller 校验 */
    public VideoDtos.VideoPageResp listVideos(Integer category, int page, int size) {
        long total = videoMapper.countVideoList(category);
        List<VideoDtos.VideoItem> list = total == 0
                ? List.of()
                : videoMapper.selectVideoList(category, (page - 1) * size, size);
        formatDates(list);
        return new VideoDtos.VideoPageResp(total, page, size, list);
    }

    /**
     * API-VD03 视频详情（公开可选 token 视角）。
     * 顺带 view_count 原子自增 +1（ADR-17：GET 带副作用为简化决策，防刷不做）。
     */
    public VideoDtos.VideoDetail getVideoDetail(long videoId, Long viewerUid) {
        Video video = videoMapper.selectById(videoId); // @TableLogic 过滤 deleted=1
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND); // 40403（含 deleted=1）
        }
        // UP 主摘要；UP 被逻辑删除 → 按视频不存在处理（API-VD03 备注）
        User up = userMapper.selectById(video.getUserId());
        if (up == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        // 原子自增并取自增后的值
        videoMapper.incrementViewCount(videoId);
        Video after = videoMapper.selectById(videoId);

        // 历史埋点（FR-HI01，Phase 4 / ADR-19）：登录用户才记（游客不记，含看自己的视频——B 站同款简化）。
        // 非事务旁路 + try-catch 吞异常记 warn：详情是读接口，绝不因历史埋点失败而 500（丢一次历史记录无害）
        if (viewerUid != null) {
            try {
                historyMapper.upsertView(viewerUid, videoId);
            } catch (Exception e) {
                log.warn("history upsert failed (uid={}, videoId={}): {}", viewerUid, videoId, e.getMessage());
            }
        }

        VideoDtos.VideoDetail d = new VideoDtos.VideoDetail();
        d.videoId = video.getId();
        d.userId = video.getUserId();
        d.title = video.getTitle();
        d.category = video.getCategory();
        d.description = video.getDescription();
        d.videoUrl = video.getVideoUrl();
        d.coverUrl = video.getCoverUrl();
        d.duration = video.getDuration();
        d.viewCount = after.getViewCount();
        d.likeCount = video.getLikeCount();
        d.createdAt = video.getCreatedAt() == null ? null : DT.format(video.getCreatedAt());
        VideoDtos.UpSummary s = new VideoDtos.UpSummary();
        s.userId = up.getId();
        s.nickname = up.getNickname();
        s.avatarUrl = up.getAvatarUrl();
        s.sign = up.getSign();
        s.followerCount = followMapper.countFollower(up.getId());
        d.up = s;
        // 视角字段（US02 范式）：isSelf 后端比对 token uid；游客恒 false
        d.isSelf = viewerUid != null && viewerUid == video.getUserId();
        d.isFollowed = viewerUid != null && followMapper.countRelation(viewerUid, video.getUserId()) > 0;
        return d;
    }

    /**
     * API-VD04 空间投稿列表（分页，公开；show_contribute 服务端过滤，FR-PF05 防绕过兑现）：
     * 本人视角全量；他人/游客且主人开关关 → 空列表（total=0，不报错、不暴露「设置但隐藏」事实）。
     */
    public VideoDtos.VideoPageResp listUserVideos(long targetId, int page, int size, Long viewerUid) {
        User target = userService.requireUser(targetId); // 目标不存在 → 40401
        boolean self = viewerUid != null && viewerUid == targetId;
        boolean visible = self || target.getShowContribute() == null || target.getShowContribute() == 1;
        if (!visible) {
            return new VideoDtos.VideoPageResp(0, page, size, List.of()); // 语义与「无投稿」不可区分
        }
        long total = videoMapper.countUserVideos(targetId);
        List<VideoDtos.VideoItem> list = total == 0
                ? List.of()
                : videoMapper.selectUserVideos(targetId, (page - 1) * size, size);
        formatDates(list);
        return new VideoDtos.VideoPageResp(total, page, size, list);
    }

    /** createdAtRaw（LocalDateTime）→ createdAt（yyyy-MM-dd HH:mm:ss）；时间格式 SSOT 见 README */
    private void formatDates(List<VideoDtos.VideoItem> list) {
        for (VideoDtos.VideoItem item : list) {
            item.createdAt = item.createdAtRaw == null ? null : DT.format(item.createdAtRaw);
            item.createdAtRaw = null; // 出参不带中转字段
        }
    }
}
