package com.bili.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 弹幕实体（TB-05，04 §3，Phase 3 / FR-IN01/IN02）。
 */
@TableName("danmaku")
public class Danmaku {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 目标视频（无物理外键，ADR-11）；服务层校验存在（40403） */
    private Long videoId;
    /** 发送者；取 token uid */
    private Long userId;
    /** 1~100 字符（码点计）；仅长度校验 */
    private String content;
    /** 出现时间偏移（毫秒）；上界 duration×1000 由服务层校验（40002） */
    private Integer timeOffset;
    /** 预留类型（1 滚动/4 底部/5 顶部）；本期恒 1，前端只实现滚动 */
    private Integer mode;
    /** 逻辑删除预留（ADR-11）；本期无删除入口 */
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getTimeOffset() { return timeOffset; }
    public void setTimeOffset(Integer timeOffset) { this.timeOffset = timeOffset; }
    public Integer getMode() { return mode; }
    public void setMode(Integer mode) { this.mode = mode; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
