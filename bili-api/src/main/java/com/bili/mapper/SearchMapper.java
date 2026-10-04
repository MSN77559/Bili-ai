package com.bili.mapper;

import com.bili.dto.UserDtos;
import com.bili.dto.VideoDtos;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 搜索 Mapper（Phase 4，FR-SE01/SE02，TD-15：LIKE %kw% 全表扫接受）。
 * keyword 已在 Service 层完成通配符预转义（\ → \\、% → \%、_ → \_，05 API-SE01 备注），
 * 此处只做参数化 LIKE CONCAT，SQL 层不再处理转义。
 * 排序口径：created_at DESC 同秒 id DESC（FO03 范式，无相关度加权）。
 */
@Mapper
public interface SearchMapper {

    /** API-SE01 视频搜索：标题模糊匹配（deleted=0，条目范式同 VD02 含 UP 摘要） */
    @Select("SELECT v.id AS videoId, v.user_id AS userId, u.nickname AS upNickname, " +
            "u.avatar_url AS upAvatarUrl, v.title, v.category, v.cover_url AS coverUrl, v.duration, " +
            "v.view_count AS viewCount, v.like_count AS likeCount, v.created_at AS createdAtRaw " +
            "FROM video v JOIN user u ON u.id = v.user_id AND u.deleted = 0 " +
            "WHERE v.deleted = 0 AND v.title LIKE CONCAT('%', #{keyword}, '%') " +
            "ORDER BY v.created_at DESC, v.id DESC LIMIT #{offset}, #{size}")
    List<VideoDtos.VideoItem> searchVideos(@Param("keyword") String keyword,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    /** API-SE01 计数（与列表同一过滤口径） */
    @Select("SELECT COUNT(*) FROM video v JOIN user u ON u.id = v.user_id AND u.deleted = 0 " +
            "WHERE v.deleted = 0 AND v.title LIKE CONCAT('%', #{keyword}, '%')")
    long countVideos(@Param("keyword") String keyword);

    /** API-SE02 用户搜索：昵称模糊匹配（deleted=0，条目范式同 FO03；isFollowed 由 Service 批量填充） */
    @Select("SELECT u.id AS userId, u.nickname, u.avatar_url AS avatarUrl, u.sign " +
            "FROM user u " +
            "WHERE u.deleted = 0 AND u.nickname LIKE CONCAT('%', #{keyword}, '%') " +
            "ORDER BY u.created_at DESC, u.id DESC LIMIT #{offset}, #{size}")
    List<UserDtos.FollowPageItem> searchUsers(@Param("keyword") String keyword,
                                              @Param("offset") int offset,
                                              @Param("size") int size);

    /** API-SE02 计数（与列表同一过滤口径） */
    @Select("SELECT COUNT(*) FROM user u " +
            "WHERE u.deleted = 0 AND u.nickname LIKE CONCAT('%', #{keyword}, '%')")
    long countUsers(@Param("keyword") String keyword);
}
