package com.bili.controller;

import com.bili.common.context.UserContext;
import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.common.result.Result;
import com.bili.dto.AuthDtos;
import com.bili.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（05：API-AU01~AU06）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** API-AU01 注册（成功即登录，ADR-12） */
    @PostMapping("/register")
    public Result<AuthDtos.LoginResp> register(@Valid @RequestBody AuthDtos.EmailPasswordReq req) {
        return Result.ok(authService.register(req.getEmail(), req.getPassword()));
    }

    /** API-AU02 登录 */
    @PostMapping("/login")
    public Result<AuthDtos.LoginResp> login(@Valid @RequestBody AuthDtos.EmailPasswordReq req) {
        return Result.ok(authService.login(req.getEmail(), req.getPassword()));
    }

    /** API-AU03 退出（语义占位：JWT 无状态，无服务端失效，TD-02） */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }

    /** API-AU04 忘记密码-发送验证码（Mock，ADR-14） */
    @PostMapping("/password/reset-code")
    public Result<Void> sendResetCode(@Valid @RequestBody AuthDtos.SendCodeReq req) {
        authService.sendResetCode(req.getEmail());
        return Result.fail(com.bili.common.result.ErrorCode.SUCCESS, "验证码已发送");
    }

    /** API-AU05 忘记密码-校验验证码（返回 resetToken） */
    @PostMapping("/password/verify-code")
    public Result<AuthDtos.ResetTokenResp> verifyCode(@Valid @RequestBody AuthDtos.VerifyCodeReq req) {
        return Result.ok(authService.verifyResetCode(req.getEmail(), req.getCode()));
    }

    /** API-AU06 忘记密码-重置密码 */
    @PostMapping("/password/reset")
    public Result<Void> reset(@Valid @RequestBody AuthDtos.ResetReq req) {
        authService.resetPassword(req.getResetToken(), req.getNewPassword());
        return Result.ok();
    }

    /** API-AU07 每日签到（登录，+5 枚当日防重；Phase 3 / FR-IN11） */
    @PostMapping("/check-in")
    public Result<AuthDtos.CheckInResp> checkIn() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED); // 理论不可达（拦截器保证）
        }
        return Result.ok(new AuthDtos.CheckInResp(authService.checkIn(uid)));
    }
}
