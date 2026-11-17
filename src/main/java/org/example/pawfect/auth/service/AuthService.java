package org.example.pawfect.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.pawfect.auth.dto.LoginRequest;
import org.example.pawfect.auth.dto.TokenResponse;
import org.example.pawfect.auth.jwt.JwtProvider;
import org.example.pawfect.global.exception.BusinessException;
import org.example.pawfect.global.exception.ErrorCode;
import org.example.pawfect.member.domain.Member;
import org.example.pawfect.member.repository.MemberRepository;
import org.example.pawfect.member.service.MemberService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(MemberService.normalizeEmail(request.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        // 이메일이 없는 경우와 비밀번호가 틀린 경우를 같은 오류로 돌려, 가입 여부를 추측하지 못하게 한다.
        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return TokenResponse.bearer(jwtProvider.createAccessToken(member.getId()),
                jwtProvider.accessTokenValiditySeconds());
    }
}
