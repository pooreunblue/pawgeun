package org.example.pawse.member.dto;

import org.example.pawse.member.domain.Member;

/** 엔티티를 그대로 내보내지 않는다. passwordHash가 응답에 섞이는 사고를 구조적으로 막는다. */
public record MemberResponse(Long id, String email, String nickname) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getNickname());
    }
}
