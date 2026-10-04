package com.bili.controller;

import com.bili.common.context.UserContext;
import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.common.result.Result;
import com.bili.dto.SearchDtos;
import com.bili.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索接口（05：API-SE01/SE02，Phase 4）。
 * 公开（白名单 GET /api/search/**，05 §6）；SE02 携带有效 token 时 isFollowed 为真实视角。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** API-SE01 视频搜索（公开；标题模糊匹配，无视角字段） */
    @GetMapping("/videos")
    public Result<SearchDtos.VideoSearchResp> videos(@RequestParam String keyword,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return Result.ok(searchService.searchVideos(keyword, page, size));
    }

    /** API-SE02 用户搜索（公开 + 可选 token：isFollowed 视角） */
    @GetMapping("/users")
    public Result<SearchDtos.UserSearchResp> users(@RequestParam String keyword,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return Result.ok(searchService.searchUsers(keyword, page, size, UserContext.getUserId()));
    }

    /** 分页参数校验（05 §1：page≥1 默认 1、size 1~50 默认 20，非法 40001） */
    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "分页参数不合法");
        }
    }
}
