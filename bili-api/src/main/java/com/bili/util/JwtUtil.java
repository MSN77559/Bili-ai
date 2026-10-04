package com.bili.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具（03 §5.1 / ADR-02）：HS256，payload 只放 uid/iat/exp。
 * 校验结果三态：NONE(未携带) / EXPIRED(过期) / INVALID(无效)，对应 40101/40102/40103。
 */
public class JwtUtil {

    public enum VerifyResult { NONE, EXPIRED, INVALID, OK }

    public record Verified(long uid) {}

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${bili.jwt.secret}") String secret,
                   @Value("${bili.jwt.expire-days}") int expireDays) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("bili.jwt.secret 必须 >= 32 字节（ADR-02）");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireMillis = expireDays * 24L * 3600L * 1000L;
    }

    /** 生成 token；expiresIn 秒数随注册/登录响应返回 */
    public record TokenResult(String token, long expiresInSeconds) {}

    public TokenResult generate(long uid) {
        long now = System.currentTimeMillis();
        String token = Jwts.builder()
                .claim("uid", uid)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMillis))
                .signWith(key)
                .compact();
        return new TokenResult(token, expireMillis / 1000);
    }

    public VerifyResult verify(String token) {
        if (token == null || token.isBlank()) return VerifyResult.NONE;
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return VerifyResult.OK;
        } catch (ExpiredJwtException e) {
            return VerifyResult.EXPIRED;
        } catch (JwtException | IllegalArgumentException e) {
            return VerifyResult.INVALID;
        }
    }

    /** 解析 uid（调用前须先 verify==OK） */
    public long parseUid(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        Number uid = claims.get("uid", Number.class);
        return uid.longValue();
    }
}
