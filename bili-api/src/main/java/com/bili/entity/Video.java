package com.bili.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 视频实体（TB-04，04 §3，Phase 2 / ADR-17）。
 */
@TableName("video")
public class Video {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** UP 主用户 ID（无物理外键，ADR-11）；投稿时取 token uid */
    private Long userId;
    private String title;
    /** 分区 1~14（ADR-10 清单顺序；名称映射 SSOT 在前端 CATEGORIES 常量） */
    private Integer category;
    /** 简介，空串而非 NULL（与 sign 同构） */
    private String description;
    /** 视频文件相对路径（/api/files/videos/xxx.mp4） */
    private String videoUrl;
    /** 封面相对路径；NULL=未上传，前端 GRADIENTS 渐变兜底（ADR-17） */
    private String coverUrl;
    /** 时长秒数（前端 loadedmetadata 读取，ADR-17） */
    private Integer duration;
    /** 播放数冗余计数（ADR-17）：详情接口原子自增；列表接口不触发 */
    private Long viewCount;
    /** 点赞数冗余计数（ADR-17/18）：FR-IN06 写路径接管 */
    private Long likeCount;
    /** 投币数冗余计数（ADR-18）：FR-IN07 投币原子 +n */
    private Long coinCount;
    /** 收藏数冗余计数（ADR-18）：FR-IN08 切换原子 ±1 */
    private Long collectCount;
    /** 逻辑删除预留（ADR-11）：deleted=1 按不存在处理（40403） */
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getCategory() { return category; }
    public void setCategory(Integer category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Long getViewCount() { return viewCount; }
    public void setViewCount(Long viewCount) { this.viewCount = viewCount; }
    public Long getLikeCount() { return likeCount; }
    public void setLikeCount(Long likeCount) { this.likeCount = likeCount; }
    public Long getCoinCount() { return coinCount; }
    public void setCoinCount(Long coinCount) { this.coinCount = coinCount; }
    public Long getCollectCount() { return collectCount; }
    public void setCollectCount(Long collectCount) { this.collectCount = collectCount; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
