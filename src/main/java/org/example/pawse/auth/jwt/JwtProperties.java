package org.example.pawse.auth.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.properties의 pawse.jwt.* 값을 타입 있는 객체로 받는다.
 *
 * @param secret               HS256 서명 키. 32바이트(256비트) 이상이어야 한다.
 * @param accessTokenValidity  액세스 토큰 유효 시간
 */
@ConfigurationProperties(prefix = "pawse.jwt")
public record JwtProperties(String secret, Duration accessTokenValidity) {
}
