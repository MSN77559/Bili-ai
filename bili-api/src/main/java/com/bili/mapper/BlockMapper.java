package com.bili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bili.entity.Block;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BlockMapper extends BaseMapper<Block> {

    /** 幂等拉黑：INSERT IGNORE（ADR-15 沿用 ADR-06 防重模式），影响行数 0 不报错 */
    @Insert("INSERT IGNORE INTO `block` (blocker_id, blocked_id) VALUES (#{blockerId}, #{blockedId})")
    int insertIgnore(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /** blocker 是否已拉黑 blocked（US02 isBlocked 视角字段 / FO01 双向校验复用） */
    @Select("SELECT COUNT(*) FROM `block` WHERE blocker_id = #{blockerId} AND blocked_id = #{blockedId}")
    long countRelation(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);
}
