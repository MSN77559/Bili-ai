package com.bili.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 消息通知实体（TB-11，04 §3，Phase 4 / FR-MS01~MS03，ADR-19）。
 * 引用 + 快照混合：sender 昵称头像 JOIN user 实时取（改名跟得上），评论内容存快照（列表零 comment JOIN）；
 * 消息删除不做（仅 is_read 标记）；评论删除不删本行（commentDeleted 由服务端批量查）。
 */
@TableName("message")
public class Message {
    /** 消息类型：1 回复我的（CM01）/ 2 收到的赞（IN01）/ 3 新粉丝（FO01） */
    public static final int TYPE_REPLY = 1;
    public static final int TYPE_LIKE = 2;
    public static final int TYPE_FOLLOW = 3;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long receiverId;
    private Long senderId;
    private Integer type;
    /** 视频引用（type=1/2 必填，type=3 为 null） */
    private Long videoId;
    /** 评论引用（仅 type=1） */
    private Long commentId;
    /** 内容快照（仅 type=1：回复内容前 200 字） */
    private String content;
    /** 0 未读 1 已读 */
    private Integer isRead;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getIsRead() { return isRead; }
    public void setIsRead(Integer isRead) { this.isRead = isRead; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
