package com.bili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bili.dto.UserDtos;
import com.bili.entity.Follow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FollowMapper extends BaseMapper<Follow> {

    /** 幂等关注：INSERT IGNORE（ADR-06），影响行数 0 不报错 */
    @Insert("INSERT IGNORE INTO follow (follower_id, following_id) VALUES (#{followerId}, #{followingId})")
    int insertIgnore(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    /** 关注数：我关注了多少人（04 §4 口径） */
    @Select("SELECT COUNT(*) FROM follow WHERE follower_id = #{userId}")
    long countFollowing(@Param("userId") Long userId);

    /** 粉丝数：多少人关注了我（04 §4 口径） */
    @Select("SELECT COUNT(*) FROM follow WHERE following_id = #{userId}")
    long countFollower(@Param("userId") Long userId);

    /** 当前登录用户是否已关注目标（isFollowed 视角字段） */
    @Select("SELECT COUNT(*) FROM follow WHERE follower_id = #{followerId} AND following_id = #{followingId}")
    long countRelation(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    /** API-FO03 关注列表（分页）：目标关注的人，按关注时间倒序（id 兜底保证分页稳定，FR-FO05 EX-7）。
     * Phase 4 双向拉黑过滤（FR-FO06）：viewerUid 非空时，条目与访问者任一方向有 block 行即隐藏（NOT EXISTS 双分支均走索引）；
     * 游客（viewerUid=null）无过滤。total 与 list 同口径（countFollowings 带同样子查询）。 */
    @Select("<script>" +
            "SELECT u.id AS userId, u.nickname, u.avatar_url AS avatarUrl, u.sign " +
            "FROM follow f JOIN user u ON u.id = f.following_id AND u.deleted = 0 " +
            "WHERE f.follower_id = #{userId} " +
            "<if test='viewerUid != null'>" +
            "AND NOT EXISTS (SELECT 1 FROM block b WHERE (b.blocker_id = #{viewerUid} AND b.blocked_id = f.following_id) " +
            "OR (b.blocker_id = f.following_id AND b.blocked_id = #{viewerUid})) " +
            "</if>" +
            "ORDER BY f.created_at DESC, f.id DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<UserDtos.FollowPageItem> selectFollowings(@Param("userId") long userId,
                                                   @Param("viewerUid") Long viewerUid,
                                                   @Param("offset") int offset,
                                                   @Param("size") int size);

    /** API-FO04 粉丝列表（分页）：关注目标的人，同上排序与拉黑过滤口径 */
    @Select("<script>" +
            "SELECT u.id AS userId, u.nickname, u.avatar_url AS avatarUrl, u.sign " +
            "FROM follow f JOIN user u ON u.id = f.follower_id AND u.deleted = 0 " +
            "WHERE f.following_id = #{userId} " +
            "<if test='viewerUid != null'>" +
            "AND NOT EXISTS (SELECT 1 FROM block b WHERE (b.blocker_id = #{viewerUid} AND b.blocked_id = f.follower_id) " +
            "OR (b.blocker_id = f.follower_id AND b.blocked_id = #{viewerUid})) " +
            "</if>" +
            "ORDER BY f.created_at DESC, f.id DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<UserDtos.FollowPageItem> selectFollowers(@Param("userId") long userId,
                                                  @Param("viewerUid") Long viewerUid,
                                                  @Param("offset") int offset,
                                                  @Param("size") int size);

    /** FO03 计数（与列表同口径的拉黑过滤） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM follow f JOIN user u ON u.id = f.following_id AND u.deleted = 0 " +
            "WHERE f.follower_id = #{userId} " +
            "<if test='viewerUid != null'>" +
            "AND NOT EXISTS (SELECT 1 FROM block b WHERE (b.blocker_id = #{viewerUid} AND b.blocked_id = f.following_id) " +
            "OR (b.blocker_id = f.following_id AND b.blocked_id = #{viewerUid})) " +
            "</if>" +
            "</script>")
    long countFollowings(@Param("userId") long userId, @Param("viewerUid") Long viewerUid);

    /** FO04 计数（与列表同口径的拉黑过滤） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM follow f JOIN user u ON u.id = f.follower_id AND u.deleted = 0 " +
            "WHERE f.following_id = #{userId} " +
            "<if test='viewerUid != null'>" +
            "AND NOT EXISTS (SELECT 1 FROM block b WHERE (b.blocker_id = #{viewerUid} AND b.blocked_id = f.follower_id) " +
            "OR (b.blocker_id = f.follower_id AND b.blocked_id = #{viewerUid})) " +
            "</if>" +
            "</script>")
    long countFollowers(@Param("userId") long userId, @Param("viewerUid") Long viewerUid);

    /** 批量取访问者已关注的用户集合（列表 isFollowed 视角，避免 N+1；ids ≤ size ≤ 50） */
    @Select("<script>SELECT following_id FROM follow WHERE follower_id = #{viewerId} " +
            "AND following_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Long> selectFollowedIds(@Param("viewerId") long viewerId, @Param("ids") List<Long> ids);
}
