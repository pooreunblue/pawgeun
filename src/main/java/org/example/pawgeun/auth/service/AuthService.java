package org.example.pawgeun.auth.service;

import org.example.pawgeun.auth.dto.LoginRequest;
import org.example.pawgeun.auth.dto.TokenResponse;
import org.example.pawgeun.auth.jwt.JwtProvider;
import org.example.pawgeun.global.exception.BusinessException;
import org.example.pawgeun.global.exception.ErrorCode;
import org.example.pawgeun.member.domain.Member;
import org.example.pawgeun.member.repository.MemberRepository;
import org.example.pawgeun.member.service.MemberService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    /** 없는 이메일로 로그인할 때도 BCrypt 비교를 한 번 하게 해서 응답 시간 차이를 줄이는 더미 해시. */
    private final String dummyHash;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(MemberService.normalizeEmail(request.email())).orElse(null);
        // 이메일이 없어도 더미 해시와 비교해 BCrypt 비용을 치른다. 같은 오류 + 비슷한 응답 시간으로 가입 여부를 숨긴다.
        String hash = (member != null) ? member.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (member == null || !matches) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return TokenResponse.bearer(jwtProvider.createAccessToken(member.getId()),
                jwtProvider.accessTokenValiditySeconds());
    }
}
