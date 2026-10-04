package com.bili.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 硬币流水实体（TB-08，04 §3，Phase 3 / FR-IN07/IN11，ADR-18）。
 * 余额单一事实在 user.coin，本表仅审计与展示；流水只插入不可修改删除。
 */
@TableName("coin_flow")
public class CoinFlow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 正=收入（签到 +5/注册 +50）、负=支出（投币 -1/-2） */
    private Integer changeAmount;
    /** 1 签到 / 2 注册赠送 / 3 投币支出 */
    private Integer bizType;
    /** 业务关联（投币=videoId；其余 NULL） */
    private Long bizId;
    /** 流水时间（签到防重按当日 biz_type=1 判断） */
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getChangeAmount() { return changeAmount; }
    public void setChangeAmount(Integer changeAmount) { this.changeAmount = changeAmount; }
    public Integer getBizType() { return bizType; }
    public void setBizType(Integer bizType) { this.bizType = bizType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
