package com.bili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bili.dto.UserDtos;
import com.bili.entity.CoinFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 硬币流水 Mapper（TB-08，Phase 3 / FR-IN07/IN11，ADR-18）。
 */
@Mapper
public interface CoinFlowMapper extends BaseMapper<CoinFlow> {

    /** API-US06 流水分页（倒序；createdAtRaw 中转后由 Service 格式化） */
    @Select("SELECT id AS flowId, change_amount AS changeAmount, biz_type AS bizType, created_at AS createdAtRaw " +
            "FROM coin_flow WHERE user_id = #{userId} " +
            "ORDER BY created_at DESC, id DESC LIMIT #{offset}, #{size}")
    List<UserDtos.CoinFlowItem> selectFlows(@Param("userId") long userId,
                                            @Param("offset") int offset,
                                            @Param("size") int size);

    /** 流水总数 */
    @Select("SELECT COUNT(*) FROM coin_flow WHERE user_id = #{userId}")
    long countFlows(@Param("userId") long userId);

    /** 签到防重（FR-IN11）：当日（东八区自然日，Service 传当日零点）是否存在 biz_type=1 行 */
    @Select("SELECT COUNT(*) FROM coin_flow WHERE user_id = #{userId} AND biz_type = 1 AND created_at >= #{todayStart}")
    long countTodayCheckIn(@Param("userId") long userId, @Param("todayStart") LocalDateTime todayStart);

    /** 投币累计枚数（FR-IN07）：本人对某视频已投的 |SUM|（biz_type=3 按 biz_id） */
    @Select("SELECT COALESCE(SUM(ABS(change_amount)), 0) FROM coin_flow " +
            "WHERE user_id = #{userId} AND biz_type = 3 AND biz_id = #{videoId}")
    int sumCoinedForVideo(@Param("userId") long userId, @Param("videoId") long videoId);
}
