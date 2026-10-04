package com.bili.common.config;

import com.bili.common.context.UserContext;
import com.bili.common.result.ErrorCode;
import com.bili.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bili.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 认证拦截器（03 §5.5 / ADR-03）：白名单放行，其余校验 Bearer token。
 * 白名单与 05 §6 保持一致。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    /** 精确白名单（方法 + 路径） */
    private static final Set<String> EXACT_WHITELIST = Set.of(
            "POST /api/auth/register",
            "POST /api/auth/login",
            "POST /api/auth/password/reset-code",
            "POST /api/auth/password/verify-code",
            "POST /api/auth/password/reset"
    );

    /** 前缀白名单 */
    private static final String FILES_PREFIX = "/api/files/";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(method)) return true; // CORS 预检
        if (EXACT_WHITELIST.contains(method + " " + path)) return true;
        if (path.startsWith(FILES_PREFIX)) return true;
        // 公开视频接口（Phase 2，05 §6）：GET /api/videos 与 GET /api/videos/{videoId 纯数字}
        // Phase 3 扩展（05 §7 注意事项 9）：+ 纯数字段可选后缀（/danmaku、/comments、/interactions，公开读）
        // —— POST /api/videos（投稿）与 POST /{id}/danmaku、/comments、/like、/coin、/favorite
        //    一律不在白名单，必须带方法判断
        if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/videos")) {
            String rest = path.equals("/api/videos") ? "" : path.substring("/api/videos/".length());
            if (!rest.isEmpty()) {
                // 剥离可选的公开读后缀（DL02/CM02/IN02），剩下的必须是纯数字 videoId
                for (String suffix : new String[]{"/danmaku", "/comments", "/interactions"}) {
                    if (rest.endsWith(suffix)) {
                        rest = rest.substring(0, rest.length() - suffix.length());
                        break;
                    }
                }
            }
            if (rest.isEmpty() || rest.matches("\\d+")) {
                // 可选 token 解析视角（VD03 isSelf/isFollowed；IN02 本人三状态）；无效 token 按游客
                optParseToken(request);
                return true;
            }
            // /api/videos/abc、/api/videos/1/like 等非白名单形态落入下方正常鉴权
            // （无 token → 40101；带 token → Controller 类型转换 40001，05 §6「不在白名单」）
        }
        // 公开搜索（Phase 4，05 §6）：GET /api/search/**——独立前缀双白名单分支（SE01/SE02）
        // 该前缀下仅 /videos 与 /users 两个 GET 子路径存在，无写操作，前缀整体放行；
        // 可选 token 供 SE02 isFollowed 视角字段使用（无效 token 按游客）。
        // 独立于 /api/videos、/api/users 的数字匹配分支，不触碰其形态（越权风险最小）
        if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/search")) {
            optParseToken(request);
            return true;
        }

        // 公开主页与关注/粉丝/投稿列表：GET /api/users/{userId}（及 /followings、/followers、/videos 后缀），
        // Phase 3 扩展（05 §6）：后缀集合追加 /favorites、/likes（IN05/IN06，show 开关服务端过滤）
        // userId 必须纯数字（防 "me" 被放行——/api/users/me/coin-flows 仅本人）
        if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/users/")) {
            String rest = path.substring("/api/users/".length());
            // 去掉可选的列表后缀（API-FO03/FO04/VD04/IN05/IN06，05 §6 白名单）
            boolean isListPath = false;
            if (rest.endsWith("/followings") || rest.endsWith("/followers") || rest.endsWith("/videos")
                    || rest.endsWith("/favorites") || rest.endsWith("/likes")) {
                isListPath = true;
                rest = rest.substring(0, rest.lastIndexOf('/'));
            }
            if (rest.matches("\\d+")) {
                // 白名单放行但尽力解析 token：携带有效 token 时设置 UserContext，
                // 供 isSelf/isFollowed/isBlocked 视角字段使用（API-US02/FO03/FO04/VD04）；token 无效则按游客处理
                optParseToken(request);
                return true;
            }
            // /api/users/me、/api/users/me/videos 等非数字段不白名单放行，落入下方正常鉴权
        }

        String auth = request.getHeader("Authorization");
        String token = null;
        if (auth != null && auth.startsWith("Bearer ")) {
            token = auth.substring(7);
        }

        JwtUtil.VerifyResult vr = jwtUtil.verify(token);
        switch (vr) {
            case NONE -> { writeError(response, ErrorCode.UNAUTHORIZED); return false; }
            case EXPIRED -> { writeError(response, ErrorCode.TOKEN_EXPIRED); return false; }
            case INVALID -> { writeError(response, ErrorCode.TOKEN_INVALID); return false; }
            case OK -> { UserContext.set(jwtUtil.parseUid(token)); return true; }
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear(); // ThreadLocal 泄漏防范（03 §5.5）
    }

    private String optToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        return auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
    }

    /** 白名单公开接口的可选 token 解析：有效则设置 UserContext（视角字段），无效按游客 */
    private void optParseToken(HttpServletRequest request) {
        String opt = optToken(request);
        if (opt != null && jwtUtil.verify(opt) == JwtUtil.VerifyResult.OK) {
            UserContext.set(jwtUtil.parseUid(opt));
        }
    }

    private void writeError(HttpServletResponse response, ErrorCode ec) throws java.io.IOException {
        response.setStatus(ec.getHttpStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ec)));
    }
}
