package com.bili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bili.dto.UserDtos;
import com.bili.entity.Message;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 消息通知 Mapper（TB-11，Phase 4 / FR-MS01~MS03，ADR-19）。
 * 埋点写入用实体 insert（BaseMapper）；此处为列表/已读/COUNT 专用 SQL。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /** API-MS01 消息列表：type 可选筛选（null=全部）、created_at 倒序（同秒 id 兜底），sender JOIN user 实时摘要 */
    @Select("<script>SELECT m.id, m.type, m.sender_id AS senderId, u.nickname AS senderNickname, " +
            "u.avatar_url AS senderAvatarUrl, m.video_id AS videoId, m.comment_id AS commentId, " +
            "m.content, m.is_read AS isRead, m.created_at AS createdAtRaw " +
            "FROM message m JOIN user u ON u.id = m.sender_id " +
            "WHERE m.receiver_id = #{receiverId} " +
            "<if test='type != null'>AND m.type = #{type} </if>" +
            "ORDER BY m.created_at DESC, m.id DESC LIMIT #{offset}, #{size}</script>")
    List<UserDtos.MessageItem> selectMessages(@Param("receiverId") long receiverId,
                                              @Param("type") Integer type,
                                              @Param("offset") int offset,
                                              @Param("size") int size);

    /** API-MS01 计数（与列表同一过滤口径） */
    @Select("<script>SELECT COUNT(*) FROM message m JOIN user u ON u.id = m.sender_id " +
            "WHERE m.receiver_id = #{receiverId} " +
            "<if test='type != null'>AND m.type = #{type}</if></script>")
    long countMessages(@Param("receiverId") long receiverId, @Param("type") Integer type);

    /** 单条消息归属查询（MS02：不存在与非本人统一 40406 防存在性泄露，先查后改） */
    @Select("SELECT * FROM message WHERE id = #{id}")
    Message selectByIdDirect(@Param("id") long id);

    /** API-MS02 单条已读（幂等：已读再读影响 0 行不报错；归属已由服务端校验） */
    @Update("UPDATE message SET is_read = 1 WHERE id = #{id} AND is_read = 0")
    int markRead(@Param("id") long id);

    /** API-MS03 全部已读（幂等：无未读影响 0 行） */
    @Update("UPDATE message SET is_read = 1 WHERE receiver_id = #{receiverId} AND is_read = 0")
    int markAllRead(@Param("receiverId") long receiverId);

    /** API-MS04 未读数（实时 COUNT，ADR-19 无冗余列） */
    @Select("SELECT COUNT(*) FROM message WHERE receiver_id = #{receiverId} AND is_read = 0")
    long countUnread(@Param("receiverId") long receiverId);

    /** 批量查评论删除状态（MS01 commentDeleted 标记：仅 type=1 条目，IN 批查防 N+1） */
    @Select("<script>SELECT c.id AS commentId, c.deleted AS deleted FROM comment c " +
            "WHERE c.id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<UserDtos.CommentDeletedRow> selectCommentDeleted(@Param("ids") List<Long> ids);
}
