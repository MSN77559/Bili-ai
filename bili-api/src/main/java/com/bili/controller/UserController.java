package com.bili.controller;

import com.bili.common.context.UserContext;
import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.common.result.Result;
import com.bili.dto.UserDtos;
import com.bili.service.BlockService;
import com.bili.service.FollowService;
import com.bili.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 用户接口（05：API-US01~US04、API-FO01/02、API-BK01/02）。
 * 注意：/api/users/me 必须显式 mapping，Spring 精确路径优先于 {userId}（05 §7 注意事项 1）。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    /** 头像类型白名单（ADR-04）：扩展名小写比对 */
    private static final Set<String> AVATAR_EXTS = Set.of("jpg", "jpeg", "png", "webp");

    private final UserService userService;
    private final FollowService followService;
    private final BlockService blockService;

    @Value("${bili.upload.dir}")
    private String uploadDir;

    public UserController(UserService userService, FollowService followService, BlockService blockService,
                          com.bili.service.VideoService videoService,
                          com.bili.service.HistoryService historyService,
                          com.bili.service.MessageService messageService) {
        this.userService = userService;
        this.followService = followService;
        this.blockService = blockService;
        this.videoService = videoService;
        this.historyService = historyService;
        this.messageService = messageService;
    }

    /** API-US01 当前用户完整资料（显式 /me mapping，唯一含 email） */
    @GetMapping("/me")
    public Result<UserDtos.MyProfile> me() {
        return Result.ok(userService.getMyProfile(requireUid()));
    }

    /** API-US02 公开主页（白名单接口；uid 从 token 可选解析——由拦截器对游客放行，此处不强制） */
    @GetMapping("/{userId}")
    public Result<UserDtos.PublicProfile> publicProfile(@PathVariable Long userId) {
        // 游客：UserContext 为空 → viewerUid=null → isSelf/isFollowed 恒 false
        return Result.ok(userService.getPublicProfile(userId, UserContext.getUserId()));
    }

    /** API-US03 修改基础资料（不接受 email，DTO 未定义该字段即静默忽略） */
    @PutMapping("/me")
    public Result<Void> updateMe(@Valid @RequestBody UserDtos.UpdateProfileReq req) {
        userService.updateMyProfile(requireUid(), req);
        return Result.ok();
    }

    /** 图片大小上限（ADR-17 分层：全局 multipart 放宽至 500MB 后，2MB 由 Service/Controller 层显式校验兜底） */
    private static final long IMAGE_MAX_BYTES = 2L * 1024 * 1024;

    /** API-US04 头像上传（multipart，上传即生效） */
    @PostMapping("/me/avatar")
    public Result<UserDtos.AvatarResp> uploadAvatar(@RequestParam("file") MultipartFile file) {
        long uid = requireUid();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择头像图片");
        }
        String ext = extOf(file.getOriginalFilename());
        if (!AVATAR_EXTS.contains(ext)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED); // 41501
        }
        // ≤2MB：全局 multipart 已放宽（500MB，ADR-17），此处显式校验为唯一防线（必做回归点：3MB 头像仍 41301）
        if (file.getSize() > IMAGE_MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(uploadDir, "avatars");
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toAbsolutePath());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "头像保存失败");
        }
        String avatarUrl = "/api/files/avatars/" + filename; // 相对路径（ADR-04）
        return Result.ok(new UserDtos.AvatarResp(userService.updateAvatar(uid, avatarUrl)));
    }

    /** API-US05 空间背景图上传（multipart，上传即生效，FR-PF07——与头像同构） */
    @PostMapping("/me/banner")
    public Result<UserDtos.BannerResp> uploadBanner(@RequestParam("file") MultipartFile file) {
        long uid = requireUid();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择背景图片");
        }
        String ext = extOf(file.getOriginalFilename());
        if (!AVATAR_EXTS.contains(ext)) { // 与头像同一白名单（ADR-04）
            throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED, "背景图仅支持 jpg/png/webp 格式"); // 41501
        }
        // ≤2MB：同头像——ADR-17 分层后显式校验（全局 multipart 已放宽）
        if (file.getSize() > IMAGE_MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(uploadDir, "banners");
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toAbsolutePath());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "背景图保存失败");
        }
        String bannerUrl = "/api/files/banners/" + filename; // 相对路径（ADR-04）
        return Result.ok(new UserDtos.BannerResp(userService.updateBanner(uid, bannerUrl)));
    }

    /** API-FO01 关注（幂等） */
    @PostMapping("/{userId}/follow")
    public Result<UserDtos.FollowResp> follow(@PathVariable Long userId) {
        return Result.ok(followService.follow(requireUid(), userId));
    }

    /** API-FO02 取关（幂等） */
    @DeleteMapping("/{userId}/follow")
    public Result<UserDtos.FollowResp> unfollow(@PathVariable Long userId) {
        return Result.ok(followService.unfollow(requireUid(), userId));
    }

    /** API-BK01 拉黑（幂等，双向自动取关，ADR-15） */
    @PostMapping("/{userId}/block")
    public Result<UserDtos.BlockResp> block(@PathVariable Long userId) {
        return Result.ok(blockService.block(requireUid(), userId));
    }

    /** API-BK02 取消拉黑（幂等，不恢复关注关系） */
    @DeleteMapping("/{userId}/block")
    public Result<UserDtos.BlockResp> unblock(@PathVariable Long userId) {
        return Result.ok(blockService.unblock(requireUid(), userId));
    }

    /** API-FO03 关注列表（分页，公开；白名单接口，viewer 从 token 可选解析——拦截器对游客放行） */
    @GetMapping("/{userId}/followings")
    public Result<UserDtos.FollowPageResp> followings(@PathVariable Long userId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return Result.ok(followService.listFollowings(requirePositive(userId), page, size, UserContext.getUserId()));
    }

    /** API-FO04 粉丝列表（分页，公开，同 FO03 范式） */
    @GetMapping("/{userId}/followers")
    public Result<UserDtos.FollowPageResp> followers(@PathVariable Long userId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return Result.ok(followService.listFollowers(requirePositive(userId), page, size, UserContext.getUserId()));
    }

    private final com.bili.service.VideoService videoService;

    /** API-VD04 空间投稿列表（分页，公开；show_contribute 服务端过滤，Phase 2） */
    @GetMapping("/{userId}/videos")
    public Result<com.bili.dto.VideoDtos.VideoPageResp> userVideos(@PathVariable Long userId,
                                                                   @RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return Result.ok(videoService.listUserVideos(requirePositive(userId), page, size, UserContext.getUserId()));
    }

    /** API-US06 我的硬币流水（登录仅本人，分页倒序；Phase 3 / FR-IN11，/me 路径不在白名单） */
    @GetMapping("/me/coin-flows")
    public Result<UserDtos.CoinFlowPageResp> myCoinFlows(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return Result.ok(userService.listCoinFlows(requireUid(), page, size));
    }

    private final com.bili.service.HistoryService historyService;

    /** API-HI01 我的观看历史（仅本人，viewed_at 倒序；Phase 4 / FR-HI02，/me 路径不在白名单） */
    @GetMapping("/me/history")
    public Result<UserDtos.HistoryPageResp> myHistory(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return Result.ok(historyService.listHistory(requireUid(), page, size));
    }

    /** API-HI02 删除单条历史（幂等，行不存在也 200；Phase 4 / FR-HI03） */
    @DeleteMapping("/me/history/{videoId}")
    public Result<Void> deleteHistoryOne(@PathVariable Long videoId) {
        historyService.deleteOne(requireUid(), requirePositive(videoId));
        return Result.ok();
    }

    /** API-HI03 清空历史（幂等；Phase 4 / FR-HI03） */
    @DeleteMapping("/me/history")
    public Result<Void> clearHistory() {
        historyService.deleteAll(requireUid());
        return Result.ok();
    }

    private final com.bili.service.MessageService messageService;

    /** API-MS01 消息列表（仅本人，type 可选筛选 1/2/3 缺省全部；Phase 4 / FR-MS01） */
    @GetMapping("/me/messages")
    public Result<UserDtos.MessagePageResp> myMessages(@RequestParam(required = false) Integer type,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return Result.ok(messageService.listMessages(requireUid(), type, page, size));
    }

    /** API-MS02 标记单条已读（幂等；不存在/非本人统一 40406；Phase 4 / FR-MS02） */
    @PostMapping("/me/messages/{id}/read")
    public Result<Void> markMessageRead(@PathVariable Long id) {
        messageService.markRead(requireUid(), requirePositive(id));
        return Result.ok();
    }

    /** API-MS03 全部消息已读（幂等；Phase 4 / FR-MS02） */
    @PostMapping("/me/messages/read-all")
    public Result<Void> markAllMessagesRead() {
        messageService.markAllRead(requireUid());
        return Result.ok();
    }

    /** API-MS04 未读消息数（实时 COUNT，NavBar 红点专用；Phase 4 / FR-MS03） */
    @GetMapping("/me/messages/unread-count")
    public Result<UserDtos.UnreadCountResp> unreadCount() {
        return Result.ok(messageService.unreadCount(requireUid()));
    }

    /** 分页参数校验（05 §1：page≥1 默认 1、size 1~50 默认 20，非法 40001） */
    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "分页参数不合法");
        }
    }

    /** path userId 正整数校验（0/负数按用户不存在处理，与既有接口口径一致） */
    private long requirePositive(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return userId;
    }

    private long requireUid() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            // 理论不可达：拦截器已保证需登录接口必有 uid
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return uid;
    }

    private String extOf(String filename) {
        if (filename == null) return "";
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
