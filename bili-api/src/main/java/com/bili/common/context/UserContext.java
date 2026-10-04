package com.bili.common.context;

/**
 * 当前登录用户上下文（03 §5.5）：ThreadLocal 持有，拦截器 afterCompletion 必清。
 */
public class UserContext {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    public static void set(Long uid) {
        HOLDER.set(uid);
    }

    public static Long getUserId() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
