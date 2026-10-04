package com.bili.service;

import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.dto.DanmakuDtos;
import com.bili.entity.Danmaku;
import com.bili.entity.Video;
import com.bili.mapper.DanmakuMapper;
import com.bili.mapper.VideoMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 弹幕服务（FR-IN01/IN02，Phase 3）。
 */
@Service
public class DanmakuService {

    private final DanmakuMapper danmakuMapper;
    private final VideoMapper videoMapper;

    public DanmakuService(DanmakuMapper danmakuMapper, VideoMapper videoMapper) {
        this.danmakuMapper = danmakuMapper;
        this.videoMapper = videoMapper;
    }

    /** API-DL01 发弹幕（登录）。校验顺序：40403 视频存在 → 40002 内容 → 40002 时间（05 备注） */
    public DanmakuDtos.SendDanmakuResp send(long videoId, long uid, DanmakuDtos.SendDanmakuReq req) {
        Video video = requireVideo(videoId);
        String content = req.getContent() == null ? "" : req.getContent().trim();
        // 码点计（emoji 按 1 计，与标题/昵称校验同款）
        int len = content.codePointCount(0, content.length());
        if (len < 1 || len > 100) {
            throw new BusinessException(ErrorCode.CONTENT_INVALID, "弹幕长度须为 1-100 字符");
        }
        Integer offset = req.getTimeOffset();
        long maxMs = video.getDuration() == null ? 0 : video.getDuration() * 1000L;
        if (offset == null || offset < 0 || offset > maxMs) {
            throw new BusinessException(ErrorCode.CONTENT_INVALID, "弹幕时间不合法");
        }
        Danmaku d = new Danmaku();
        d.setVideoId(videoId);
        d.setUserId(uid); // token uid，不信客户端
        d.setContent(content);
        d.setTimeOffset(offset);
        d.setMode(1); // 滚动（mode 列预留类型，本期恒 1）
        danmakuMapper.insert(d);
        return new DanmakuDtos.SendDanmakuResp(d.getId());
    }

    /** API-DL02 弹幕列表（公开白名单）：time_offset 升序全量（无分页） */
    public DanmakuDtos.DanmakuListResp list(long videoId) {
        requireVideo(videoId);
        List<DanmakuDtos.DanmakuItem> list = danmakuMapper.selectByVideo(videoId);
        return new DanmakuDtos.DanmakuListResp(list.size(), list);
    }

    /** 视频存在校验（含 deleted=1 → 40403，@TableLogic 过滤） */
    private Video requireVideo(long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        return video;
    }
}
