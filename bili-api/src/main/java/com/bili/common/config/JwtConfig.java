package com.bili.common.config;

import com.bili.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT Bean 独立配置：避免 WebConfig ↔ AuthInterceptor 经 JwtUtil 形成循环依赖。
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtUtil jwtUtil(@Value("${bili.jwt.secret}") String secret,
                           @Value("${bili.jwt.expire-days}") int expireDays) {
        return new JwtUtil(secret, expireDays);
    }
}
