package com.bili.service;

import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.dto.UserDtos;
import com.bili.entity.User;
import com.bili.mapper.BlockMapper;
import com.bili.mapper.FollowMapper;
import com.bili.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 用户服务（FR-PR01~03、FR-PF01~06）。
 */
@Service
public class UserService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final BlockMapper blockMapper;
    private final com.bili.mapper.CoinFlowMapper coinFlowMapper;

    public UserService(UserMapper userMapper, FollowMapper followMapper, BlockMapper blockMapper,
                       com.bili.mapper.CoinFlowMapper coinFlowMapper) {
        this.userMapper = userMapper;
        this.followMapper = followMapper;
        this.blockMapper = blockMapper;
        this.coinFlowMapper = coinFlowMapper;
    }

    /** API-US01：当前用户完整资料（唯一含 email 的接口，05 §2.3） */
    public UserDtos.MyProfile getMyProfile(long uid) {
        User user = requireUser(uid);
        UserDtos.MyProfile p = new UserDtos.MyProfile();
        p.userId = user.getId();
        p.email = user.getEmail();
        p.nickname = user.getNickname();
        p.avatarUrl = user.getAvatarUrl();
        p.bannerUrl = user.getBannerUrl(); // FR-PF07
        p.gender = user.getGender();
        p.birthday = dateStr(user.getBirthday());
        p.sign = user.getSign();
        p.showContribute = flag(user.getShowContribute());
        p.showFavorites = flag(user.getShowFavorites());
        p.showLikes = flag(user.getShowLikes());
        p.showBirthday = flag(user.getShowBirthday());
        p.showFollows = flag(user.getShowFollows()); // FR-FO06（Phase 4）
        p.coin = user.getCoin() == null ? 0 : user.getCoin(); // FR-PF08（列 NOT NULL，防御式兜底）
        p.createdAt = user.getCreatedAt() == null ? null : DT.format(user.getCreatedAt());
        return p;
    }

    /** API-US02：公开主页（实时 COUNT 计数 + isSelf/isFollowed/isBlocked 视角 + 不含 email/createdAt） */
    public UserDtos.PublicProfile getPublicProfile(long targetId, Long viewerUid) {
        User user = requireUser(targetId);
        UserDtos.PublicProfile p = new UserDtos.PublicProfile();
        p.userId = user.getId();
        p.nickname = user.getNickname();
        p.avatarUrl = user.getAvatarUrl();
        p.bannerUrl = user.getBannerUrl(); // FR-PF07：对所有访客公开
        p.gender = user.getGender();
        p.sign = user.getSign();
        // 实时 COUNT（ADR-07；口径见 04 §4）
        p.followingCount = followMapper.countFollowing(targetId);
        p.followerCount = followMapper.countFollower(targetId);
        // 视角字段：isSelf 由后端比对 token uid 与 path userId（FR-PR02，防篡改）
        p.isSelf = viewerUid != null && viewerUid == targetId;
        p.isFollowed = viewerUid != null
                && followMapper.countRelation(viewerUid, targetId) > 0; // 游客恒 false
        // 拉黑视角（FR-BK01）：单向——只查「访问者是否拉黑了目标」；被拉黑方不被告知（恒 false）
        p.isBlocked = viewerUid != null
                && blockMapper.countRelation(viewerUid, targetId) > 0;
        // 空间 tab 可见性（FR-PF05）与生日可见性（FR-PF06）：本人恒可见；他人/游客按主人开关。
        // birthday 裁剪必须在 isSelf 计算之后（依赖 self），null 与「未设置」不可区分（刻意设计）
        boolean self = p.isSelf;
        p.showContribute = self || flag(user.getShowContribute());
        p.showFavorites = self || flag(user.getShowFavorites());
        p.showLikes = self || flag(user.getShowLikes());
        p.birthday = self || flag(user.getShowBirthday()) ? dateStr(user.getBirthday()) : null;
        return p;
    }

    /** 查询用户，不存在（含逻辑删除）抛 40401（FR-PR03） */
    public User requireUser(long uid) {
        User user = userMapper.selectById(uid); // @TableLogic 自动过滤 deleted=1
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /** API-US03：修改基础资料（birthday null/空=清空；不接受 email，ADR-08） */
    public void updateMyProfile(long uid, UserDtos.UpdateProfileReq req) {
        User user = requireUser(uid);
        // 昵称 trim 后 2-16 字符（FR-PF02：长度按 trim 后计，emoji 按码点计）
        String nickname = req.getNickname() == null ? "" : req.getNickname().trim();
        int len = nickname.codePointCount(0, nickname.length());
        if (len < 2 || len > 16) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "昵称长度须为 2-16 字符");
        }
        LocalDate birthday = null;
        if (req.getBirthday() != null && !req.getBirthday().isBlank()) {
            try {
                birthday = LocalDate.parse(req.getBirthday());
            } catch (DateTimeParseException e) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "生日日期不合法");
            }
            if (birthday.isAfter(LocalDate.now())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "生日日期不合法"); // EX-2
            }
        }
        User update = new User();
        update.setId(user.getId());
        update.setNickname(nickname);
        update.setGender(req.getGender());
        update.setBirthday(birthday);
        update.setSign(req.getSign() == null ? "" : req.getSign());
        // 空间展示开关（FR-PF05）与生日公开开关（FR-PF06）：null=不改；MyBatis-Plus updateById 跳过 null 字段
        update.setShowContribute(req.getShowContribute());
        update.setShowFavorites(req.getShowFavorites());
        update.setShowLikes(req.getShowLikes());
        update.setShowBirthday(req.getShowBirthday());
        update.setShowFollows(req.getShowFollows()); // FR-FO06（Phase 4）：不传=不修改
        userMapper.updateById(update);
    }

    /** API-US04：头像上传即生效（ADR-04）——更新 avatar_url 并返回新值 */
    public String updateAvatar(long uid, String avatarUrl) {
        User update = new User();
        update.setId(uid);
        update.setAvatarUrl(avatarUrl);
        userMapper.updateById(update);
        return avatarUrl;
    }

    /** API-US05：背景图上传即生效（FR-PF07，与头像同构）——更新 banner_url 并返回新值 */
    public String updateBanner(long uid, String bannerUrl) {
        User update = new User();
        update.setId(uid);
        update.setBannerUrl(bannerUrl);
        userMapper.updateById(update);
        return bannerUrl;
    }

    /** API-US06：我的硬币流水分页（Phase 3 / FR-IN11，倒序；createdAtRaw → 字符串格式化） */
    public UserDtos.CoinFlowPageResp listCoinFlows(long uid, int page, int size) {
        long total = coinFlowMapper.countFlows(uid);
        java.util.List<UserDtos.CoinFlowItem> list = total == 0
                ? java.util.List.of()
                : coinFlowMapper.selectFlows(uid, (page - 1) * size, size);
        for (UserDtos.CoinFlowItem item : list) {
            item.createdAt = item.createdAtRaw == null ? null : DT.format(item.createdAtRaw);
            item.createdAtRaw = null;
        }
        return new UserDtos.CoinFlowPageResp(total, page, size, list);
    }

    private String dateStr(LocalDate d) {
        return d == null ? null : d.toString(); // yyyy-MM-dd
    }

    /** 空间开关空值兜底：null 按 1（公开）处理 */
    private boolean flag(Integer v) {
        return v == null || v == 1;
    }
}
