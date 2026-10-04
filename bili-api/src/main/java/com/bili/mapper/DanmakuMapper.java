package com.bili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bili.dto.DanmakuDtos;
import com.bili.entity.Danmaku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 弹幕 Mapper（TB-05，Phase 3）。
 * 拉取口径：按 time_offset 升序全量（无分页，课程项目量级可控），仅 deleted=0（@TableLogic）。
 */
@Mapper
public interface DanmakuMapper extends BaseMapper<Danmaku> {

    /** API-DL02 全量弹幕（JOIN user 取发送者昵称；发送者被逻辑删除时弹幕照常返回） */
    @Select("SELECT d.id AS danmakuId, d.content, d.time_offset AS timeOffset, d.mode, u.nickname " +
            "FROM danmaku d JOIN user u ON u.id = d.user_id " +
            "WHERE d.video_id = #{videoId} AND d.deleted = 0 " +
            "ORDER BY d.time_offset ASC, d.id ASC")
    List<DanmakuDtos.DanmakuItem> selectByVideo(@org.apache.ibatis.annotations.Param("videoId") long videoId);

    /** 弹幕总数（与列表同一过滤口径） */
    @Select("SELECT COUNT(*) FROM danmaku WHERE video_id = #{videoId} AND deleted = 0")
    long countByVideo(@org.apache.ibatis.annotations.Param("videoId") long videoId);
}
