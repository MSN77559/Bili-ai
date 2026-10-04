package com.bili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bili.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 硬币原子条件扣减（FR-IN07，ADR-18）：影响行数 0 = 余额不足（40905）。
     * 禁止读改写——WHERE coin >= n 保证并发双投不透支。
     */
    @Update("UPDATE user SET coin = coin - #{n} WHERE id = #{uid} AND coin >= #{n}")
    int decreaseCoin(@Param("uid") long uid, @Param("n") int n);

    /** 硬币原子加（FR-IN11 签到 +5 / FR-IN01 注册赠送） */
    @Update("UPDATE user SET coin = coin + #{n} WHERE id = #{uid}")
    int increaseCoin(@Param("uid") long uid, @Param("n") int n);
}
