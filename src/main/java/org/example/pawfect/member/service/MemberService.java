package org.example.pawfect.member.service;

import lombok.RequiredArgsConstructor;
import org.example.pawfect.global.exception.BusinessException;
import org.example.pawfect.global.exception.ErrorCode;
import org.example.pawfect.member.domain.Member;
import org.example.pawfect.member.repository.MemberRepository;
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
        return memberRepository.save(member);
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
        return email.trim().toLowerCase();
    }
}
