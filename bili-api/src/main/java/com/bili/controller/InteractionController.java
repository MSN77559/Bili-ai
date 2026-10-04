package com.bili.controller;

import com.bili.common.context.UserContext;
import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.common.result.Result;
import com.bili.dto.InteractDtos;
import com.bili.dto.VideoDtos;
import com.bili.service.InteractionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 互动接口（05：API-IN01~IN06，Phase 3 / FR-IN06~IN10，ADR-18）。
 * GET interactions 与 GET /api/users/{id}/favorites|likes 为白名单公开接口（可选 token 视角）；
 * POST like/coin/favorite 一律需登录。
 */
@RestController
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService,
                                 com.bili.service.FavoriteFolderService folderService) {
        this.interactionService = interactionService;
        this.folderService = folderService;
    }

    /** API-IN01 点赞切换（幂等） */
    @PostMapping("/api/videos/{videoId}/like")
    public Result<InteractDtos.LikeResp> toggleLike(@PathVariable Long videoId) {
        return Result.ok(interactionService.toggleLike(requireVideoId(videoId), requireUid()));
    }

    /** API-IN02 互动状态聚合（公开可选 token；游客三状态恒 false） */
    @GetMapping("/api/videos/{videoId}/interactions")
    public Result<InteractDtos.InteractionStatus> interactions(@PathVariable Long videoId) {
        // 拦截器对白名单接口尽力解析 token；游客 UserContext 为空
        return Result.ok(interactionService.getStatus(requireVideoId(videoId), UserContext.getUserId()));
    }

    /** API-IN03 投币（1/2 枚） */
    @PostMapping("/api/videos/{videoId}/coin")
    public Result<InteractDtos.CoinResp> coin(@PathVariable Long videoId,
                                              @RequestBody InteractDtos.CoinReq req) {
        return Result.ok(interactionService.coin(requireVideoId(videoId), requireUid(), req));
    }

    /** API-IN04 收藏切换（幂等；body 可选 folderId——不传 = Phase 3 切换语义，传 = 选夹收藏/移夹） */
    @PostMapping("/api/videos/{videoId}/favorite")
    public Result<InteractDtos.FavoriteResp> toggleFavorite(@PathVariable Long videoId,
                                                            @RequestBody(required = false) InteractDtos.FavoriteReq req) {
        Long folderId = req == null ? null : req.getFolderId();
        return Result.ok(interactionService.toggleFavorite(requireVideoId(videoId), requireUid(), folderId));
    }

    /** API-IN05 空间收藏列表（分页，公开；show_favorites 服务端过滤） */
    @GetMapping("/api/users/{userId}/favorites")
    public Result<VideoDtos.VideoPageResp> favorites(@PathVariable Long userId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return Result.ok(interactionService.listFavorites(requireUserId(userId), page, size, UserContext.getUserId()));
    }

    /** API-IN06 空间点赞列表（分页，公开；show_likes 服务端过滤） */
    @GetMapping("/api/users/{userId}/likes")
    public Result<VideoDtos.VideoPageResp> likes(@PathVariable Long userId,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return Result.ok(interactionService.listLikes(requireUserId(userId), page, size, UserContext.getUserId()));
    }

    private final com.bili.service.FavoriteFolderService folderService;

    /** API-IN07 我的收藏夹列表（仅本人，全量非分页；Phase 4 / FR-IN12，/me 路径不在白名单） */
    @GetMapping("/api/users/me/favorite-folders")
    public Result<InteractDtos.FolderListResp> myFolders() {
        return Result.ok(folderService.listFolders(requireUid()));
    }

    /** API-IN08 创建收藏夹（Phase 4 / FR-IN13；同名 40909、名称 1~20 码点 40001） */
    @PostMapping("/api/users/me/favorite-folders")
    public Result<InteractDtos.FolderCreateResp> createFolder(@jakarta.validation.Valid
                                                              @RequestBody InteractDtos.FolderNameReq req) {
        return Result.ok(folderService.create(requireUid(), req.getName()));
    }

    /** API-IN09 重命名收藏夹（Phase 4 / FR-IN14；默认夹 40908、非本人夹 40405） */
    @PutMapping("/api/users/me/favorite-folders/{folderId}")
    public Result<Void> renameFolder(@PathVariable Long folderId,
                                     @RequestBody InteractDtos.FolderNameReq req) {
        folderService.rename(requireUid(), requireFolderId(folderId), req.getName());
        return Result.ok();
    }

    /** API-IN10 删除收藏夹（Phase 4 / FR-IN14；默认夹 40908、夹内条目同事务移入默认夹） */
    @DeleteMapping("/api/users/me/favorite-folders/{folderId}")
    public Result<Void> deleteFolder(@PathVariable Long folderId) {
        folderService.delete(requireUid(), requireFolderId(folderId));
        return Result.ok();
    }

    /** API-IN11 夹内容列表（Phase 4 / FR-IN16；仅本人、分页收藏时间倒序、非本人夹 40405） */
    @GetMapping("/api/users/me/favorite-folders/{folderId}/favorites")
    public Result<VideoDtos.VideoPageResp> folderFavorites(@PathVariable Long folderId,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return Result.ok(interactionService.listFolderFavorites(requireUid(), requireFolderId(folderId), page, size));
    }

    private long requireFolderId(Long folderId) {
        if (folderId == null || folderId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "收藏夹 ID 不合法");
        }
        return folderId;
    }

    private long requireVideoId(Long videoId) {
        if (videoId == null || videoId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "视频 ID 不合法");
        }
        return videoId;
    }

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND); // 与既有接口口径一致
        }
        return userId;
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "分页参数不合法");
        }
    }

    private long requireUid() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return uid;
    }
}
