package org.example.pawfect.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.pawfect.global.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 한 번 실행된다.
 * Authorization: Bearer {토큰} 이 있으면 검증하고, 성공하면 SecurityContext에 로그인 상태를 심는다.
 * 실패해도 여기서 응답을 끊지 않는다. 인증이 필요한 주소인지는 SecurityConfig가 판단한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String INVALID_TOKEN_ATTRIBUTE = "pawfect.invalidToken";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Long memberId = jwtProvider.parseMemberId(token);
                var authentication = new UsernamePasswordAuthenticationToken(
                        memberId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException e) {
                // 진입점(401 응답)이 "토큰이 없음"과 "토큰이 잘못됨"을 구분할 수 있게 표시만 남긴다.
                request.setAttribute(INVALID_TOKEN_ATTRIBUTE, true);
            }
        }
        chain.doFilter(request, response);
    }
}
