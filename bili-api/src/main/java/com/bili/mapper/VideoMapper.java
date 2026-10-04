package com.bili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bili.dto.VideoDtos;
import com.bili.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 视频 Mapper（TB-04，Phase 2）。
 * 排序口径：created_at DESC 同秒 id DESC 兜底（FO03 范式）；仅 deleted=0（@TableLogic）。
 */
@Mapper
public interface VideoMapper extends BaseMapper<Video> {

    /** API-VD02 全站列表（category=null 为全站；JOIN user 取 UP 信息） */
    @Select("<script>SELECT v.id AS videoId, v.user_id AS userId, u.nickname AS upNickname, " +
            "u.avatar_url AS upAvatarUrl, v.title, v.category, v.cover_url AS coverUrl, v.duration, " +
            "v.view_count AS viewCount, v.like_count AS likeCount, v.created_at AS createdAtRaw " +
            "FROM video v JOIN user u ON u.id = v.user_id AND u.deleted = 0 " +
            "WHERE v.deleted = 0 " +
            "<if test='category != null'>AND v.category = #{category} </if>" +
            "ORDER BY v.created_at DESC, v.id DESC LIMIT #{offset}, #{size}</script>")
    List<VideoDtos.VideoItem> selectVideoList(@Param("category") Integer category,
                                              @Param("offset") int offset,
                                              @Param("size") int size);

    /** API-VD02 计数（与列表同一过滤口径） */
    @Select("<script>SELECT COUNT(*) FROM video v JOIN user u ON u.id = v.user_id AND u.deleted = 0 " +
            "WHERE v.deleted = 0 <if test='category != null'>AND v.category = #{category}</if></script>")
    long countVideoList(@Param("category") Integer category);

    /** API-VD04 空间投稿列表（不含 UP 字段——主人页头已知） */
    @Select("SELECT v.id AS videoId, v.user_id AS userId, v.title, v.category, v.cover_url AS coverUrl, " +
            "v.duration, v.view_count AS viewCount, v.like_count AS likeCount, v.created_at AS createdAtRaw " +
            "FROM video v WHERE v.user_id = #{userId} AND v.deleted = 0 " +
            "ORDER BY v.created_at DESC, v.id DESC LIMIT #{offset}, #{size}")
    List<VideoDtos.VideoItem> selectUserVideos(@Param("userId") long userId,
                                               @Param("offset") int offset,
                                               @Param("size") int size);

    /** API-VD04 计数（真实总数；服务端过滤关闭时 Controller 不调用，直接返回空） */
    @Select("SELECT COUNT(*) FROM video WHERE user_id = #{userId} AND deleted = 0")
    long countUserVideos(@Param("userId") long userId);

    /** API-VD03 view_count 原子自增（ADR-17：SET col = col + 1，非读改写防并发丢计数） */
    @Update("UPDATE video SET view_count = view_count + 1 WHERE id = #{videoId} AND deleted = 0")
    int incrementViewCount(@Param("videoId") long videoId);

    // ---- Phase 3 互动计数（ADR-18：原子 SQL，减法 GREATEST 防负）----

    /** FR-IN06 点赞：like_count 原子 +1 */
    @Update("UPDATE video SET like_count = like_count + 1 WHERE id = #{videoId} AND deleted = 0")
    int incrementLikeCount(@Param("videoId") long videoId);

    /** FR-IN06 取消点赞：like_count 原子 -1（下限 0，防状态位与计数竞态重复减） */
    @Update("UPDATE video SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{videoId} AND deleted = 0")
    int decrementLikeCount(@Param("videoId") long videoId);

    /** FR-IN07 投币：coin_count 原子 +n */
    @Update("UPDATE video SET coin_count = coin_count + #{n} WHERE id = #{videoId} AND deleted = 0")
    int incrementCoinCount(@Param("videoId") long videoId, @Param("n") int n);

    /** FR-IN08 收藏：collect_count 原子 +1 */
    @Update("UPDATE video SET collect_count = collect_count + 1 WHERE id = #{videoId} AND deleted = 0")
    int incrementCollectCount(@Param("videoId") long videoId);

    /** FR-IN08 取消收藏：collect_count 原子 -1（下限 0） */
    @Update("UPDATE video SET collect_count = GREATEST(collect_count - 1, 0) WHERE id = #{videoId} AND deleted = 0")
    int decrementCollectCount(@Param("videoId") long videoId);
}
