package com.bili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bili.dto.VideoDtos;
import com.bili.entity.VideoLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 视频点赞 Mapper（TB-07，Phase 3 / FR-IN06，ADR-18）。
 * 不用 @TableLogic：取消/复活靠显式 UPDATE 状态位（deleted 0↔1）。
 */
@Mapper
public interface VideoLikeMapper extends BaseMapper<VideoLike> {

    /** 查本人对某视频的点赞行（无行=从未赞过；deleted=1=已取消） */
    @Select("SELECT * FROM video_like WHERE user_id = #{userId} AND video_id = #{videoId}")
    VideoLike selectRow(@Param("userId") long userId, @Param("videoId") long videoId);

    /** 状态位切换（0↔1）：点赞=置 0，取消=置 1；影响行数 0 说明行不存在 */
    @Update("UPDATE video_like SET deleted = #{deleted} WHERE user_id = #{userId} AND video_id = #{videoId}")
    int updateState(@Param("userId") long userId, @Param("videoId") long videoId, @Param("deleted") int deleted);

    /** API-IN06 空间点赞列表（分页倒序；JOIN video 取卡片字段——条目范式同 VD02 含 UP 摘要） */
    @Select("SELECT v.id AS videoId, v.user_id AS userId, u.nickname AS upNickname, u.avatar_url AS upAvatarUrl, " +
            "v.title, v.category, v.cover_url AS coverUrl, v.duration, v.view_count AS viewCount, " +
            "v.like_count AS likeCount, v.created_at AS createdAtRaw " +
            "FROM video_like l JOIN video v ON v.id = l.video_id AND v.deleted = 0 " +
            "JOIN user u ON u.id = v.user_id AND u.deleted = 0 " +
            "WHERE l.user_id = #{userId} AND l.deleted = 0 " +
            "ORDER BY l.created_at DESC, l.id DESC LIMIT #{offset}, #{size}")
    List<VideoDtos.VideoItem> selectLikedVideos(@Param("userId") long userId,
                                                 @Param("offset") int offset,
                                                 @Param("size") int size);

    /** 空间点赞计数（与列表同一过滤口径；服务端过滤关闭时 Service 不调用，直接返回空） */
    @Select("SELECT COUNT(*) FROM video_like l JOIN video v ON v.id = l.video_id AND v.deleted = 0 " +
            "JOIN user u ON u.id = v.user_id AND u.deleted = 0 " +
            "WHERE l.user_id = #{userId} AND l.deleted = 0")
    long countLikedVideos(@Param("userId") long userId);
}
