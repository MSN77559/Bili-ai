package com.bili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bili.dto.UserDtos;
import com.bili.entity.History;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 观看历史 Mapper（TB-10，Phase 4 / FR-HI01~HI03，ADR-19）。
 */
@Mapper
public interface HistoryMapper extends BaseMapper<History> {

    /**
     * 历史埋点 upsert（VD03 内，登录用户才调用）：
     * uk_user_video 命中 → viewed_at 更新置顶；未命中 → 插新行。单语句天然并发安全（ADR-06 唯一索引兜底）。
     */
    @Insert("INSERT INTO history (user_id, video_id, viewed_at) VALUES (#{userId}, #{videoId}, NOW()) " +
            "ON DUPLICATE KEY UPDATE viewed_at = NOW()")
    int upsertView(@Param("userId") long userId, @Param("videoId") long videoId);

    /** API-HI01 历史列表：viewed_at 倒序（同秒 id 兜底），JOIN video 摘要（仅 deleted=0 条目；视频 UP 一并 JOIN） */
    @Select("SELECT h.video_id AS videoId, v.title, v.cover_url AS coverUrl, v.duration, " +
            "v.user_id AS upUserId, u.nickname AS upNickname, h.viewed_at AS viewedAtRaw " +
            "FROM history h JOIN video v ON v.id = h.video_id AND v.deleted = 0 " +
            "JOIN user u ON u.id = v.user_id AND u.deleted = 0 " +
            "WHERE h.user_id = #{userId} " +
            "ORDER BY h.viewed_at DESC, h.id DESC LIMIT #{offset}, #{size}")
    List<UserDtos.HistoryItem> selectHistory(@Param("userId") long userId,
                                             @Param("offset") int offset,
                                             @Param("size") int size);

    /** API-HI01 计数（与列表同一过滤口径：视频被删的历史行不占 total） */
    @Select("SELECT COUNT(*) FROM history h JOIN video v ON v.id = h.video_id AND v.deleted = 0 " +
            "JOIN user u ON u.id = v.user_id AND u.deleted = 0 " +
            "WHERE h.user_id = #{userId}")
    long countHistory(@Param("userId") long userId);

    /** API-HI02 删单条（幂等：行不存在影响 0 行不报错） */
    @Delete("DELETE FROM history WHERE user_id = #{userId} AND video_id = #{videoId}")
    int deleteOne(@Param("userId") long userId, @Param("videoId") long videoId);

    /** API-HI03 清空（幂等：无历史时影响 0 行） */
    @Delete("DELETE FROM history WHERE user_id = #{userId}")
    int deleteAll(@Param("userId") long userId);
}
