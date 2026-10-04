package com.bili.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 评论实体（TB-06，04 §3，Phase 3 / FR-IN03~IN05，两级楼中楼）。
 */
@TableName("comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 目标视频；父评论与回复都必须属于同一视频（服务层校验 40404） */
    private Long videoId;
    /** 评论者；仅本人可删（FR-IN05） */
    private Long userId;
    /** 0=父评论；非 0=楼中楼回复（两级结构，回复不再嵌套） */
    private Long parentId;
    /** 楼中楼内「回复某条回复」时的对象；NULL=直接回复父评论 */
    private Long replyToUserId;
    /** 1~500 字符（码点计）；仅长度校验 */
    private String content;
    /** 评论点赞预留水位（本期不接写路径） */
    private Long likeCount;
    /** 逻辑删除：父评论删除时回复级联置 1（ADR-11） */
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getReplyToUserId() { return replyToUserId; }
    public void setReplyToUserId(Long replyToUserId) { this.replyToUserId = replyToUserId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getLikeCount() { return likeCount; }
    public void setLikeCount(Long likeCount) { this.likeCount = likeCount; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
