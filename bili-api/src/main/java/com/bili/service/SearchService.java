package com.bili.service;

import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.dto.SearchDtos;
import com.bili.dto.UserDtos;
import com.bili.mapper.FollowMapper;
import com.bili.mapper.SearchMapper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 搜索服务（FR-SE01/SE02，Phase 4）。
 * 公开接口（白名单 GET /api/search/**）；keyword 校验与通配符预转义在此收口。
 */
@Service
public class SearchService {

    /** 与 MyProfile.createdAt 等处同款格式化口径（05 全局约定） */
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 关键词长度上限（码点计，05 API-SE01） */
    private static final int KEYWORD_MAX = 50;

    private final SearchMapper searchMapper;
    private final FollowMapper followMapper;

    public SearchService(SearchMapper searchMapper, FollowMapper followMapper) {
        this.searchMapper = searchMapper;
        this.followMapper = followMapper;
    }

    /** API-SE01 视频搜索（标题模糊匹配，条目范式同 VD02） */
    public SearchDtos.VideoSearchResp searchVideos(String keyword, int page, int size) {
        String kw = requireKeyword(keyword);
        long total = searchMapper.countVideos(kw);
        List<com.bili.dto.VideoDtos.VideoItem> items =
                total == 0 ? List.of() : searchMapper.searchVideos(kw, (page - 1) * size, size);
        items.forEach(i -> {
            if (i.createdAtRaw != null) {
                i.createdAt = DT.format(i.createdAtRaw);
                i.createdAtRaw = null;
            }
        });
        return new SearchDtos.VideoSearchResp(total, page, size, items);
    }

    /** API-SE02 用户搜索（昵称模糊匹配，条目范式同 FO03 含 isFollowed 视角） */
    public SearchDtos.UserSearchResp searchUsers(String keyword, int page, int size, Long viewerUid) {
        String kw = requireKeyword(keyword);
        long total = searchMapper.countUsers(kw);
        List<UserDtos.FollowPageItem> items =
                total == 0 ? List.of() : searchMapper.searchUsers(kw, (page - 1) * size, size);
        fillIsFollowed(items, viewerUid);
        return new SearchDtos.UserSearchResp(total, page, size, items);
    }

    /** keyword 校验 + 通配符预转义：trim 后 1~50 码点（40001）；`\`→`\\`、`%`→`\%`、`_`→`\_`（先 `\` 后其余，顺序固定） */
    private String requireKeyword(String keyword) {
        if (keyword == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "搜索关键词不合法");
        }
        String kw = keyword.trim();
        if (kw.isEmpty() || kw.codePointCount(0, kw.length()) > KEYWORD_MAX) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "搜索关键词不合法");
        }
        return kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** isFollowed 视角填充（FO03 fillIsFollowed 同款防 N+1）：游客恒 false；条目为访问者本人恒 false */
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
}
