package com.bili.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 观看历史实体（TB-10，04 §3，Phase 4 / FR-HI01~HI03，ADR-19）。
 * 无 deleted 列（物理删除：删单条/清空即删行，重新观看自然插新行复活——与 video_like 状态位模型刻意不同）；
 * 无 created_at（viewed_at 即业务时间，插入与更新同列）；不用 @TableLogic。
 */
@TableName("history")
public class History {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long videoId;
    /** 最后观看时间（重看即更新置顶；HI01 排序口径） */
    private LocalDateTime viewedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public LocalDateTime getViewedAt() { return viewedAt; }
    public void setViewedAt(LocalDateTime viewedAt) { this.viewedAt = viewedAt; }
}
