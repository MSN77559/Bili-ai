package com.bili.service;

import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.dto.UserDtos;
import com.bili.mapper.HistoryMapper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 观看历史服务（FR-HI01~HI03，Phase 4 / ADR-19）。
 * 仅本人（/me 路径不在白名单）；埋点 upsert 在 VideoService（非事务旁路），本类只管读与删。
 */
@Service
public class HistoryService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HistoryMapper historyMapper;

    public HistoryService(HistoryMapper historyMapper) {
        this.historyMapper = historyMapper;
    }

    /** API-HI01 我的观看历史（分页，viewed_at 倒序） */
    public UserDtos.HistoryPageResp listHistory(long uid, int page, int size) {
        long total = historyMapper.countHistory(uid);
        List<UserDtos.HistoryItem> list =
                total == 0 ? List.of() : historyMapper.selectHistory(uid, (page - 1) * size, size);
        for (UserDtos.HistoryItem item : list) {
            item.viewedAt = item.viewedAtRaw == null ? null : DT.format(item.viewedAtRaw);
            item.viewedAtRaw = null; // 出参不带中转字段
        }
        return new UserDtos.HistoryPageResp(total, page, size, list);
    }

    /** API-HI02 删单条（幂等：行不存在也 200）。videoId 非正整数 40001 */
    public void deleteOne(long uid, long videoId) {
        if (videoId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "视频 ID 不合法");
        }
        historyMapper.deleteOne(uid, videoId);
    }

    /** API-HI03 清空（幂等：无历史时 200） */
    public void deleteAll(long uid) {
        historyMapper.deleteAll(uid);
    }
}
