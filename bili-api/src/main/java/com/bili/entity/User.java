package com.bili.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户实体（TB-01，04 §3）。
 */
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    /** 空间主页背景图相对路径（FR-PF07）：NOT NULL，未上传时为列默认值 default.png（与 avatarUrl 同构） */
    private String bannerUrl;
    private Integer gender;
    private LocalDate birthday;
    private String sign;
    /** 空间展示开关（FR-PF05）：投稿/收藏夹/点赞 tab 对他人是否可见，默认 1=公开 */
    private Integer showContribute;
    private Integer showFavorites;
    private Integer showLikes;
    /** 生日公开开关（FR-PF06）：生日对他人是否可见，默认 1=公开 */
    private Integer showBirthday;
    /** 关注/粉丝列表公开开关（FR-FO06，Phase 4）：单开关管两侧，默认 1=公开；本人恒可见，US02 计数照常 */
    private Integer showFollows;
    /** 硬币余额（FR-PF08）：NOT NULL DEFAULT 0，本期只读（获取/消费 Phase 3） */
    private Integer coin;
    /** 逻辑删除预留（ADR-11）：查询习惯性带 deleted=0 */
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }
    public Integer getGender() { return gender; }
    public void setGender(Integer gender) { this.gender = gender; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }
    public String getSign() { return sign; }
    public void setSign(String sign) { this.sign = sign; }
    public Integer getShowContribute() { return showContribute; }
    public void setShowContribute(Integer showContribute) { this.showContribute = showContribute; }
    public Integer getShowFavorites() { return showFavorites; }
    public void setShowFavorites(Integer showFavorites) { this.showFavorites = showFavorites; }
    public Integer getShowLikes() { return showLikes; }
    public void setShowLikes(Integer showLikes) { this.showLikes = showLikes; }
    public Integer getShowBirthday() { return showBirthday; }
    public void setShowBirthday(Integer showBirthday) { this.showBirthday = showBirthday; }
    public Integer getShowFollows() { return showFollows; }
    public void setShowFollows(Integer showFollows) { this.showFollows = showFollows; }
    public Integer getCoin() { return coin; }
    public void setCoin(Integer coin) { this.coin = coin; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
