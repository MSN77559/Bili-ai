package com.bili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bili.dto.CommentDtos;
import com.bili.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 评论 Mapper（TB-06，Phase 3）。
 * 排序口径：父评论 created_at DESC 同秒 id DESC 兜底（FO03 范式）；回复 created_at ASC。
 * 回复昵称（reply_to_user_id → nickname）由本 Mapper 的映射 SQL 冗余返回（05 API-CM02）。
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * API-CM02 父评论分页（含发布者摘要）；replyCount 与首屏回复由 Service 批量组装（防 N+1）。
     * createdAtRaw 为 LocalDateTime 中转字段，Service 格式化为 yyyy-MM-dd HH:mm:ss。
     */
    @Select("SELECT c.id AS commentId, c.user_id AS userId, u.nickname, u.avatar_url AS avatarUrl, " +
            "c.content, c.parent_id AS parentId, c.reply_to_user_id AS replyToUserId, c.created_at AS createdAtRaw " +
            "FROM comment c JOIN user u ON u.id = c.user_id " +
            "WHERE c.video_id = #{videoId} AND c.parent_id = 0 AND c.deleted = 0 " +
            "ORDER BY c.created_at DESC, c.id DESC LIMIT #{offset}, #{size}")
    List<CommentDtos.CommentItem> selectParents(@Param("videoId") long videoId,
                                                 @Param("offset") int offset,
                                                 @Param("size") int size);

    /** 父评论总数（与列表同一过滤口径） */
    @Select("SELECT COUNT(*) FROM comment WHERE video_id = #{videoId} AND parent_id = 0 AND deleted = 0")
    long countParents(@Param("videoId") long videoId);

    /**
     * 指定父评论集合的有效回复计数（parentIds 逐组）：Service 按 parentId 分组装配 replyCount。
     * 空集合调用由 Service 规避（MyBatis foreach 空数组会生成非法 IN ()）。
     */
    @Select("<script>SELECT parent_id AS parentId, COUNT(*) AS cnt FROM comment " +
            "WHERE deleted = 0 AND parent_id IN " +
            "<foreach collection='parentIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach> " +
            "GROUP BY parent_id</script>")
    List<CommentDtos.ReplyCountRow> countRepliesByParents(@Param("parentIds") List<Long> parentIds);

    /**
     * 指定父评论的首屏回复（每父前 2 条，时间正序）：窗口函数 ROW_NUMBER 按 parent 分组取前 N。
     * MySQL 8 支持；Service 再按 parentId 分组塞回各父评论的 replies 字段。
     */
    @Select("<script>SELECT t.commentId, t.userId, t.nickname, t.avatarUrl, t.content, t.parentId, " +
            "t.replyToUserId, t.replyToNickname, t.createdAtRaw FROM " +
            "(SELECT c.id AS commentId, c.user_id AS userId, u.nickname, u.avatar_url AS avatarUrl, c.content, " +
            "c.parent_id AS parentId, c.reply_to_user_id AS replyToUserId, ru.nickname AS replyToNickname, " +
            "c.created_at AS createdAtRaw, " +
            "ROW_NUMBER() OVER (PARTITION BY c.parent_id ORDER BY c.created_at ASC, c.id ASC) AS rn " +
            "FROM comment c JOIN user u ON u.id = c.user_id " +
            "LEFT JOIN user ru ON ru.id = c.reply_to_user_id " +
            "WHERE c.deleted = 0 AND c.parent_id IN " +
            "<foreach collection='parentIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>) t " +
            "WHERE t.rn &lt;= 2 ORDER BY t.parentId, t.createdAtRaw, t.commentId</script>")
    List<CommentDtos.ReplyItem> selectFirstReplies(@Param("parentIds") List<Long> parentIds);

    /** API-CM02 回复分页（parent 参数：全量楼中楼，时间正序） */
    @Select("SELECT c.id AS commentId, c.user_id AS userId, u.nickname, u.avatar_url AS avatarUrl, " +
            "c.content, c.parent_id AS parentId, c.reply_to_user_id AS replyToUserId, ru.nickname AS replyToNickname, " +
            "c.created_at AS createdAtRaw " +
            "FROM comment c JOIN user u ON u.id = c.user_id " +
            "LEFT JOIN user ru ON ru.id = c.reply_to_user_id " +
            "WHERE c.parent_id = #{parentId} AND c.deleted = 0 " +
            "ORDER BY c.created_at ASC, c.id ASC LIMIT #{offset}, #{size}")
    List<CommentDtos.ReplyItem> selectReplies(@Param("parentId") long parentId,
                                               @Param("offset") int offset,
                                               @Param("size") int size);

    /** 指定父评论的有效回复总数 */
    @Select("SELECT COUNT(*) FROM comment WHERE parent_id = #{parentId} AND deleted = 0")
    long countReplies(@Param("parentId") long parentId);

    /** 父评论删除时级联逻辑删其全部回复（FR-IN05 主流程 5；同事务） */
    @Update("UPDATE comment SET deleted = 1 WHERE parent_id = #{parentId} AND deleted = 0")
    int cascadeDeleteReplies(@Param("parentId") long parentId);

    /** CM01 响应：单条评论摘要回读（JOIN user 冗余本人昵称头像与 replyTo 昵称；父/回复通用） */
    @Select("SELECT c.id AS commentId, c.user_id AS userId, u.nickname, u.avatar_url AS avatarUrl, " +
            "c.content, c.parent_id AS parentId, c.reply_to_user_id AS replyToUserId, ru.nickname AS replyToNickname, " +
            "c.created_at AS createdAtRaw " +
            "FROM comment c JOIN user u ON u.id = c.user_id " +
            "LEFT JOIN user ru ON ru.id = c.reply_to_user_id " +
            "WHERE c.id = #{commentId} AND c.deleted = 0")
    CommentDtos.ReplyItem selectCommentSummary(@Param("commentId") long commentId);
}
