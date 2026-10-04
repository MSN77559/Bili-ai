package com.bili.service;

import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.dto.UserDtos;
import com.bili.entity.Follow;
import com.bili.entity.Message;
import com.bili.mapper.BlockMapper;
import com.bili.mapper.FollowMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 关注服务（FR-FO01/02/05，ADR-06；Phase 4 加新粉丝消息埋点）。
 * 幂等语义：重复关注/取关均成功且状态不变（INSERT IGNORE / DELETE，影响行数 0 不报错）。
 */
@Service
public class FollowService {

    private final FollowMapper followMapper;
    private final BlockMapper blockMapper;
    private final UserService userService;
    private final MessageService messageService;

    public FollowService(FollowMapper followMapper, BlockMapper blockMapper, UserService userService,
                         MessageService messageService) {
        this.followMapper = followMapper;
        this.blockMapper = blockMapper;
        this.userService = userService;
        this.messageService = messageService;
    }

    /** API-FO03 关注列表（分页，公开，FR-FO05；Phase 4 加 show_follows 门与双向拉黑过滤，FR-FO06）。
     * page/size 已由 Controller 校验合法 */
    public UserDtos.FollowPageResp listFollowings(long targetId, int page, int size, Long viewerUid) {
        com.bili.entity.User target = userService.requireUser(targetId); // 目标不存在 → 40401
        if (!followsVisible(target, viewerUid)) {
            return new UserDtos.FollowPageResp(0, page, size, List.of()); // 200 空列表，PF05 范式
        }
        Long filterUid = viewerUid; // 登录访问者视角过滤；游客无过滤（null）
        long total = followMapper.countFollowings(targetId, filterUid);
        List<UserDtos.FollowPageItem> items = followMapper.selectFollowings(targetId, filterUid, (page - 1) * size, size);
        fillIsFollowed(items, viewerUid);
        return new UserDtos.FollowPageResp(total, page, size, items);
    }

    /** API-FO04 粉丝列表（分页，公开，FR-FO05；过滤语义同 FO03） */
    public UserDtos.FollowPageResp listFollowers(long targetId, int page, int size, Long viewerUid) {
        com.bili.entity.User target = userService.requireUser(targetId);
        if (!followsVisible(target, viewerUid)) {
            return new UserDtos.FollowPageResp(0, page, size, List.of());
        }
        Long filterUid = viewerUid;
        long total = followMapper.countFollowers(targetId, filterUid);
        List<UserDtos.FollowPageItem> items = followMapper.selectFollowers(targetId, filterUid, (page - 1) * size, size);
        fillIsFollowed(items, viewerUid);
        return new UserDtos.FollowPageResp(total, page, size, items);
    }

    /** show_follows 隐私门（FR-FO06）：本人恒全量；主人关 + 他人/游客视角 → 隐藏（US02 计数照常，与 show_favorites 先例同构） */
    private boolean followsVisible(com.bili.entity.User target, Long viewerUid) {
        return viewerUid != null && viewerUid == target.getId()
                || target.getShowFollows() == null || target.getShowFollows() == 1;
    }

    /** isFollowed 视角填充（与 API-US02 范式同构）：游客恒 false；条目为访问者本人恒 false */
    private void fillIsFollowed(List<UserDtos.FollowPageItem> items, Long viewerUid) {
        if (viewerUid == null || items.isEmpty()) {
            return; // 默认 false
        }
        List<Long> ids = items.stream().map(i -> i.userId).filter(id -> id != viewerUid).toList();
        if (ids.isEmpty()) {
            return;
        }
        Set<Long> followed = new HashSet<>(followMapper.selectFollowedIds(viewerUid, ids));
        for (UserDtos.FollowPageItem item : items) {
            item.isFollowed = viewerUid != item.userId && followed.contains(item.userId);
        }
    }

    /** API-FO01 关注（幂等）。校验顺序：40902 自己 → 40401 不存在 → 40904 拉黑 → INSERT。
     * Phase 4 起仅 INSERT IGNORE 影响行数=1（真正新建关系）时同事务埋点「新粉丝」通知（FR-MS01，ADR-19；
     * 幂等重复关注不产生消息——幂等接口的副作用必须同样幂等）。 */
    @Transactional
    public UserDtos.FollowResp follow(long followerId, long followingId) {
        if (followerId == followingId) {
            throw new BusinessException(ErrorCode.CANNOT_FOLLOW_SELF); // 40902，UI 不是安全边界
        }
        userService.requireUser(followingId); // 目标不存在 → 40401
        // 双向拉黑校验（FR-FO01 EX-7，ADR-15）：任一方向存在 block 行均拒绝
        if (blockMapper.countRelation(followerId, followingId) > 0
                || blockMapper.countRelation(followingId, followerId) > 0) {
            throw new BusinessException(ErrorCode.FOLLOW_BLOCKED); // 40904
        }
        int inserted = followMapper.insertIgnore(followerId, followingId); // 唯一索引兜底 + IGNORE 幂等
        if (inserted == 1) {
            messageService.notify(followingId, followerId, Message.TYPE_FOLLOW, null, null, null);
        }
        return new UserDtos.FollowResp(true, followMapper.countFollower(followingId));
    }

    /** API-FO02 取关（幂等） */
    public UserDtos.FollowResp unfollow(long followerId, long followingId) {
        userService.requireUser(followingId);
        followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFollowingId, followingId));
        return new UserDtos.FollowResp(false, followMapper.countFollower(followingId));
    }
}
