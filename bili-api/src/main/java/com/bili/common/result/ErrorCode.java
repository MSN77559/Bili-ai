package com.bili.common.result;

/**
 * 错误码枚举（05-API接口设计 §3.1 错误码总表）。
 * 5 位数字 = HTTP 状态码(3位) + 2位流水。
 */
public enum ErrorCode {
    SUCCESS(0, 200, "success"),
    PARAM_INVALID(40001, 400, "参数校验失败"),
    UNAUTHORIZED(40101, 401, "未登录"),
    TOKEN_EXPIRED(40102, 401, "登录已过期"),
    TOKEN_INVALID(40103, 401, "未登录"),
    USER_NOT_FOUND(40401, 404, "用户不存在"),
    EMAIL_NOT_REGISTERED(40402, 404, "该邮箱未注册"),
    VIDEO_NOT_FOUND(40403, 404, "视频不存在"), // Phase 2（API-VD03）
    COMMENT_NOT_FOUND(40404, 404, "评论区已不存在"), // Phase 3（API-CM01/CM03；无权删除与不存在同码，不暴露存在性）
    FOLDER_NOT_FOUND(40405, 404, "收藏夹不存在"), // Phase 4（API-IN08~IN11；非本人夹与不存在同码，不暴露存在性）
    MESSAGE_NOT_FOUND(40406, 404, "消息不存在"), // Phase 4（API-MS02；非本人消息与不存在同码，CM03 范式）
    CONTENT_INVALID(40002, 400, "内容校验失败"), // Phase 3（弹幕 1-100/评论 1-500/投币枚数/time_offset，message 场景化）
    EMAIL_REGISTERED(40901, 409, "该邮箱已注册"),
    CANNOT_FOLLOW_SELF(40902, 409, "不能关注自己"),
    CANNOT_BLOCK_SELF(40903, 409, "不能拉黑自己"),
    FOLLOW_BLOCKED(40904, 409, "无法关注该用户"),
    COIN_NOT_ENOUGH(40905, 409, "硬币不足"), // Phase 3（API-IN03，ADR-18；40905 起流水——40903/40904 已被 BK 占用）
    ALREADY_CHECKED_IN(40906, 409, "今日已签到"), // Phase 3（API-AU07）
    COIN_LIMIT_EXCEEDED(40907, 409, "已投过 2 枚硬币"), // Phase 3（API-IN03）
    DEFAULT_FOLDER_IMMUTABLE(40908, 409, "默认收藏夹不可删除或重命名"), // Phase 4（API-IN09/IN10，ADR-20）
    FOLDER_NAME_DUPLICATED(40909, 409, "收藏夹名称已存在"), // Phase 4（API-IN08/IN09，uk_user_name 兜底）
    FILE_TOO_LARGE(41301, 413, "上传文件过大"), // ADR-17 分层：头像/背景 Service 层 2MB 校验 + 视频 500MB 全局拦截共用
    FILE_TYPE_UNSUPPORTED(41501, 415, "仅支持 jpg/png/webp 格式"),
    SEND_TOO_FREQUENT(42901, 429, "发送过于频繁，请稍后再试"),
    INTERNAL_ERROR(50000, 500, "服务器内部错误");

    private final int code;
    private final int httpStatus;
    private final String message;

    ErrorCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
    public String getMessage() { return message; }
}
