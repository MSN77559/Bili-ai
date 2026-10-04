package com.bili.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 收藏夹实体（TB-12，04 §3，Phase 4 / FR-IN12~IN16，ADR-20）。
 * 默认夹兜底：注册即建「默认收藏夹」（is_default=1）、不可删不可改名（40908）、删普通夹条目移入默认夹。
 */
@TableName("favorite_folder")
public class FavoriteFolder {

    /** 默认夹固定名称（注册事务三写创建） */
    public static final String DEFAULT_NAME = "默认收藏夹";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 名称 1~20 字（码点计）；uk(user_id, name) 防同名 */
    private String name;
    /** 1 默认收藏夹（不可删不可改名） */
    private Integer isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getIsDefault() { return isDefault; }
    public void setIsDefault(Integer isDefault) { this.isDefault = isDefault; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
