package com.bili.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 认证模块 DTO（05 API-AU01/02/04/05/06）。
 * 字段校验规则 SSOT 在 02 FR-AU01/FR-AU05；邮箱统一小写由 Service 处理。
 */
public final class AuthDtos {
    private AuthDtos() {}

    /** 注册/登录请求：邮箱正则与 FR-AU01 设计备注一致 */
    public static class EmailPasswordReq {
        @NotBlank(message = "邮箱不能为空")
        @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "邮箱格式不正确")
        @Size(max = 254, message = "邮箱长度不能超过 254 字符")
        private String email;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度须为 6-32 位")
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /** 登录/注册成功响应（ADR-12 注册即登录，结构一致） */
    public static class LoginResp {
        public String token;
        public long expiresIn;
        public UserSummary user;

        public static class UserSummary {
            public long userId;
            public String nickname;
            public String avatarUrl;

            public UserSummary(long userId, String nickname, String avatarUrl) {
                this.userId = userId;
                this.nickname = nickname;
                this.avatarUrl = avatarUrl;
            }
        }
    }

    /** 忘记密码第一步：发送验证码 */
    public static class SendCodeReq {
        @NotBlank(message = "邮箱不能为空")
        @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "邮箱格式不正确")
        private String email;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    /** 忘记密码第二步：校验验证码 */
    public static class VerifyCodeReq {
        @NotBlank(message = "邮箱不能为空")
        @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "邮箱格式不正确")
        private String email;

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "验证码应为 6 位数字")
        private String code;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    /** 忘记密码第三步：重置密码 */
    public static class ResetReq {
        @NotBlank(message = "resetToken 不能为空")
        private String resetToken;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度须为 6-32 位")
        private String newPassword;

        public String getResetToken() { return resetToken; }
        public void setResetToken(String resetToken) { this.resetToken = resetToken; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    /** 第二步成功响应 */
    public static class ResetTokenResp {
        public final String resetToken;
        public final long expiresIn;

        public ResetTokenResp(String resetToken, long expiresIn) {
            this.resetToken = resetToken;
            this.expiresIn = expiresIn;
        }
    }

    /** API-AU07 签到响应（Phase 3 / FR-IN11）：签到后余额 */
    public static class CheckInResp {
        public final int coin;

        public CheckInResp(int coin) {
            this.coin = coin;
        }
    }
}
