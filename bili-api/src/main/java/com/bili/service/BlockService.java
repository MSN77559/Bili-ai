package com.bili.service;

import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.dto.UserDtos;
import com.bili.entity.Block;
import com.bili.entity.Follow;
import com.bili.mapper.BlockMapper;
import com.bili.mapper.FollowMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 拉黑服务（FR-BK01/02，ADR-15）。
 * 幂等语义：重复拉黑/取消拉黑均成功且状态不变（INSERT IGNORE / DELETE，影响行数 0 不报错）。
 * 拉黑 = 同一事务内双向取关 + 插入 block 行；取消拉黑不恢复关注关系。
 */
@Service
public class BlockService {

    private final BlockMapper blockMapper;
    private final FollowMapper followMapper;
    private final UserService userService;

    public BlockService(BlockMapper blockMapper, FollowMapper followMapper, UserService userService) {
        this.blockMapper = blockMapper;
        this.followMapper = followMapper;
        this.userService = userService;
    }

    /** API-BK01 拉黑（幂等）。多语句写操作必须事务（ADR-15，区别于 ADR-06 单语句） */
    @Transactional
    public UserDtos.BlockResp block(long blockerId, long blockedId) {
        if (blockerId == blockedId) {
            throw new BusinessException(ErrorCode.CANNOT_BLOCK_SELF); // 40903
        }
        userService.requireUser(blockedId); // 目标不存在 → 40401
        // 双向自动取关（若存在）：删 A→B 与 B→A 两个方向的 follow 行
        followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, blockerId)
                .eq(Follow::getFollowingId, blockedId));
        followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, blockedId)
                .eq(Follow::getFollowingId, blockerId));
        blockMapper.insertIgnore(blockerId, blockedId); // 唯一索引兜底 + IGNORE 幂等
        return new UserDtos.BlockResp(true, followMapper.countFollower(blockedId));
    }

    /** API-BK02 取消拉黑（幂等，单语句无需事务；不恢复关注关系） */
    public UserDtos.BlockResp unblock(long blockerId, long blockedId) {
        userService.requireUser(blockedId);
        blockMapper.delete(new LambdaQueryWrapper<Block>()
                .eq(Block::getBlockerId, blockerId)
                .eq(Block::getBlockedId, blockedId));
        return new UserDtos.BlockResp(false, followMapper.countFollower(blockedId));
    }
}
