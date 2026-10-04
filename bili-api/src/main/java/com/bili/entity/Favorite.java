package com.bili.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 收藏实体（TB-09，04 §3，Phase 3 / FR-IN08/IN09；Phase 4 加 folder_id 单夹归属，ADR-20）。
 * deleted 幂等切换状态位（同 video_like 范式，不用 @TableLogic）。
 */
@TableName("favorite")
public class Favorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long videoId;
    /** 所属收藏夹（Phase 4 单夹归属；uk_user_video 下「从指定夹移除」语义不存在，取消恒为整体取消） */
    private Long folderId;
    /** 0 有效 1 已取消（幂等切换状态位） */
    private Integer deleted;
    /** 收藏时间（IN05 列表排序口径） */
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
