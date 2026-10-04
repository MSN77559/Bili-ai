package com.bili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bili.dto.VideoDtos;
import com.bili.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 收藏 Mapper（TB-09，Phase 3 / FR-IN08/IN09，ADR-18）。
 * deleted 状态位范式同 video_like（不用 @TableLogic）。
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /** 查本人对某视频的收藏行 */
    @Select("SELECT * FROM favorite WHERE user_id = #{userId} AND video_id = #{videoId}")
    Favorite selectRow(@Param("userId") long userId, @Param("videoId") long videoId);

    /** 状态位切换（0↔1）：收藏=置 0，取消=置 1 */
    @Update("UPDATE favorite SET deleted = #{deleted} WHERE user_id = #{userId} AND video_id = #{videoId}")
    int updateState(@Param("userId") long userId, @Param("videoId") long videoId, @Param("deleted") int deleted);

    /** API-IN04 带 folderId 已收藏分支：移入指定夹（同夹幂等 no-op——影响行数 0 可接受） */
    @Update("UPDATE favorite SET folder_id = #{folderId} WHERE user_id = #{userId} AND video_id = #{videoId} AND deleted = 0")
    int moveFolder(@Param("userId") long userId, @Param("videoId") long videoId, @Param("folderId") long folderId);

    /** API-IN04 不带 folderId 的收藏/重激活分支：归默认夹（防残留旧夹 id，05 API-IN04 备注） */
    @Update("UPDATE favorite SET deleted = #{deleted}, folder_id = #{folderId} WHERE user_id = #{userId} AND video_id = #{videoId}")
    int updateStateWithFolder(@Param("userId") long userId, @Param("videoId") long videoId,
                              @Param("deleted") int deleted, @Param("folderId") long folderId);

    /** API-IN11 夹内容列表（分页收藏时间倒序；条目范式同 IN05 含 UP 摘要，JOIN video deleted=0 同口径） */
    @Select("SELECT v.id AS videoId, v.user_id AS userId, u.nickname AS upNickname, u.avatar_url AS upAvatarUrl, " +
            "v.title, v.category, v.cover_url AS coverUrl, v.duration, v.view_count AS viewCount, " +
            "v.like_count AS likeCount, v.created_at AS createdAtRaw " +
            "FROM favorite f JOIN video v ON v.id = f.video_id AND v.deleted = 0 " +
            "JOIN user u ON u.id = v.user_id AND u.deleted = 0 " +
            "WHERE f.user_id = #{userId} AND f.folder_id = #{folderId} AND f.deleted = 0 " +
            "ORDER BY f.created_at DESC, f.id DESC LIMIT #{offset}, #{size}")
    List<VideoDtos.VideoItem> selectFolderVideos(@Param("userId") long userId,
                                                 @Param("folderId") long folderId,
                                                 @Param("offset") int offset,
                                                 @Param("size") int size);

    /** 夹内容计数（与列表同一过滤口径） */
    @Select("SELECT COUNT(*) FROM favorite f JOIN video v ON v.id = f.video_id AND v.deleted = 0 " +
            "JOIN user u ON u.id = v.user_id AND u.deleted = 0 " +
            "WHERE f.user_id = #{userId} AND f.folder_id = #{folderId} AND f.deleted = 0")
    long countFolderVideos(@Param("userId") long userId, @Param("folderId") long folderId);

    /** API-IN05 空间收藏列表（分页倒序；条目范式同 VD02 含 UP 摘要——收藏列表中视频主人非空间主人） */
    @Select("SELECT v.id AS videoId, v.user_id AS userId, u.nickname AS upNickname, u.avatar_url AS upAvatarUrl, " +
            "v.title, v.category, v.cover_url AS coverUrl, v.duration, v.view_count AS viewCount, " +
            "v.like_count AS likeCount, v.created_at AS createdAtRaw " +
            "FROM favorite f JOIN video v ON v.id = f.video_id AND v.deleted = 0 " +
            "JOIN user u ON u.id = v.user_id AND u.deleted = 0 " +
            "WHERE f.user_id = #{userId} AND f.deleted = 0 " +
            "ORDER BY f.created_at DESC, f.id DESC LIMIT #{offset}, #{size}")
    List<VideoDtos.VideoItem> selectFavoriteVideos(@Param("userId") long userId,
                                                    @Param("offset") int offset,
                                                    @Param("size") int size);

    /** 空间收藏计数（与列表同一过滤口径） */
    @Select("SELECT COUNT(*) FROM favorite f JOIN video v ON v.id = f.video_id AND v.deleted = 0 " +
            "JOIN user u ON u.id = v.user_id AND u.deleted = 0 " +
            "WHERE f.user_id = #{userId} AND f.deleted = 0")
    long countFavoriteVideos(@Param("userId") long userId);
}
