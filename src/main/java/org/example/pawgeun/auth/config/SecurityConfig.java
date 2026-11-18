package org.example.pawgeun.auth.config;

import jakarta.servlet.http.HttpServletResponse;
import org.example.pawgeun.auth.jwt.JwtAuthenticationFilter;
import org.example.pawgeun.auth.jwt.JwtProperties;
import org.example.pawgeun.auth.jwt.JwtProvider;
import org.example.pawgeun.global.exception.ErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider) throws Exception {
        http
                // 세션 쿠키가 아니라 Authorization 헤더로 인증하므로 CSRF 공격 대상이 아니다.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 인증 없이 보호된 주소에 접근했을 때: 401. */
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> {
            boolean invalid = request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE) != null;
            writeError(response, invalid ? ErrorCode.INVALID_TOKEN : ErrorCode.UNAUTHORIZED);
        };
    }

    /** 로그인은 했지만 권한이 부족할 때: 403. */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> writeError(response, ErrorCode.FORBIDDEN);
    }

    private void writeError(HttpServletResponse response, ErrorCode code) throws java.io.IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // 고정된 문자열만 담으므로 직접 조립해도 이스케이프 문제가 없다.
        response.getWriter().write("{\"code\":\"" + code.name() + "\",\"message\":\"" + code.getMessage() + "\"}");
    }
}
