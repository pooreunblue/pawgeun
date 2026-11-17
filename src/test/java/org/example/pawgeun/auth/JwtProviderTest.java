package org.example.pawgeun.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import org.example.pawgeun.auth.jwt.JwtProperties;
import org.example.pawgeun.auth.jwt.JwtProvider;
import org.example.pawgeun.global.exception.BusinessException;
import org.example.pawgeun.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

/** 스프링을 띄우지 않는 순수 단위 테스트. 1초도 걸리지 않는다. */
class JwtProviderTest {

    private static final String SECRET = "unit-test-secret-key-0123456789-abcdefghij";

    private JwtProvider provider(String secret, Duration validity) {
        return new JwtProvider(new JwtProperties(secret, validity));
    }

    @Test
    void 발급한_토큰에서_회원_id를_되찾는다() {
        JwtProvider provider = provider(SECRET, Duration.ofMinutes(30));

        String token = provider.createAccessToken(42L);

        assertThat(provider.parseMemberId(token)).isEqualTo(42L);
    }

    @Test
    void 만료된_토큰은_거부한다() {
        JwtProvider provider = provider(SECRET, Duration.ofMinutes(30));
        String expired = Jwts.builder()
                .issuer("pawgeun").subject("1")
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> provider.parseMemberId(expired))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    void 다른_키로_서명한_토큰은_거부한다() {
        String forged = provider("another-secret-key-0123456789-abcdefghijkl", Duration.ofMinutes(30))
                .createAccessToken(1L);

        assertThatThrownBy(() -> provider(SECRET, Duration.ofMinutes(30)).parseMemberId(forged))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 내용을_바꾼_토큰은_거부한다() {
        JwtProvider provider = provider(SECRET, Duration.ofMinutes(30));
        String[] parts = provider.createAccessToken(1L).split("\\.");
        // payload를 {"sub":"999"...}로 바꿔치기. 서명은 그대로라 검증에서 걸려야 한다.
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"iss\":\"pawgeun\",\"sub\":\"999\"}".getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + payload + "." + parts[2];

        assertThatThrownBy(() -> provider.parseMemberId(tampered)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 쓰레기_문자열은_거부한다() {
        JwtProvider provider = provider(SECRET, Duration.ofMinutes(30));

        assertThatThrownBy(() -> provider.parseMemberId("not-a-jwt")).isInstanceOf(BusinessException.class);
    }

    @Test
    void 키가_256비트보다_짧으면_기동_단계에서_실패한다() {
        assertThatThrownBy(() -> provider("short", Duration.ofMinutes(30)))
                .isInstanceOf(WeakKeyException.class);
    }
}
