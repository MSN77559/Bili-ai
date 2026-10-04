package com.bili.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 视频点赞实体（TB-07，04 §3，Phase 3 / FR-IN06，ADR-18）。
 * deleted 为幂等切换状态位：0=已赞、1=已取消（不用物理删行，保留痕迹简化实现）。
 * 注意：不用 @TableLogic——取消/复活靠显式 UPDATE 状态位，查询需手工带 deleted 条件。
 */
@TableName("video_like")
public class VideoLike {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long videoId;
    /** 0 有效 1 已取消（幂等切换状态位，ADR-18） */
    private Integer deleted;
    /** 首次点赞时间（IN06 列表排序口径） */
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
