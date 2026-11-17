package org.example.pawfect.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.example.pawfect.global.exception.BusinessException;
import org.example.pawfect.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/** 토큰의 발급과 검증만 맡는다. HTTP나 Spring Security는 모른다. */
@Component
public class JwtProvider {

    private static final String ISSUER = "pawfect";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtProvider(JwtProperties properties) {
        this.properties = properties;
        // Keys.hmacShaKeyFor: 키가 256비트 미만이면 WeakKeyException으로 서버 기동 단계에서 실패한다.
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long memberId) {
        Date now = new Date();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(memberId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + properties.accessTokenValidity().toMillis()))
                .signWith(key)
                .compact();
    }

    /** 서명, 만료, 형식을 모두 검증하고 회원 id를 돌려준다. 하나라도 어긋나면 INVALID_TOKEN. */
    public Long parseMemberId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    public long accessTokenValiditySeconds() {
        return properties.accessTokenValidity().toSeconds();
    }
}
