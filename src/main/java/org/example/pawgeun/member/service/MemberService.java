package org.example.pawgeun.member.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.example.pawgeun.global.exception.BusinessException;
import org.example.pawgeun.global.exception.ErrorCode;
import org.example.pawgeun.member.domain.Member;
import org.example.pawgeun.member.repository.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member signUp(String email, String rawPassword, String nickname) {
        String normalized = normalizeEmail(email);
        if (memberRepository.existsByEmail(normalized)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        Member member = Member.builder()
                .email(normalized)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .nickname(nickname)
                .build();
        try {
            // saveAndFlush: 즉시 DB에 반영해야 유니크 제약 위반을 여기서 잡을 수 있다.
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // 동시 가입으로 existsByEmail 검사를 둘 다 통과한 경우
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
    }

    public Member getById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional
    public void delete(Long id) {
        memberRepository.delete(getById(id));
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
