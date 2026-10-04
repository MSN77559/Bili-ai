package com.bili.dto;

/**
 * 弹幕模块 DTO（05：API-DL01/DL02，Phase 3 / FR-IN01/IN02）。
 */
public final class DanmakuDtos {
    private DanmakuDtos() {}

    /** API-DL01 请求：发弹幕（timeOffset 为发送瞬间视频 currentTime 毫秒） */
    public static class SendDanmakuReq {
        private String content;
        private Integer timeOffset;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Integer getTimeOffset() { return timeOffset; }
        public void setTimeOffset(Integer timeOffset) { this.timeOffset = timeOffset; }
    }

    /** API-DL01 响应：落库即返回 */
    public static class SendDanmakuResp {
        public final long danmakuId;

        public SendDanmakuResp(long danmakuId) {
            this.danmakuId = danmakuId;
        }
    }

    /** API-DL02 弹幕条目（含发送者昵称） */
    public static class DanmakuItem {
        public long danmakuId;
        public String content;
        /** 毫秒偏移 */
        public int timeOffset;
        /** 本期恒 1（滚动；mode 列预留类型切换） */
        public int mode;
        public String nickname;

        public DanmakuItem() {} // MyBatis 映射需要无参构造
    }

    /** API-DL02 全量响应（无分页，课程项目量级可控） */
    public static class DanmakuListResp {
        public final long total;
        public final java.util.List<DanmakuItem> list;

        public DanmakuListResp(long total, java.util.List<DanmakuItem> list) {
            this.total = total;
            this.list = list;
        }
    }
}
