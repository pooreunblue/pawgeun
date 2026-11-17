package org.example.pawse.member.controller;

import lombok.RequiredArgsConstructor;
import org.example.pawse.member.dto.MemberResponse;
import org.example.pawse.member.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /** principal은 JwtAuthenticationFilter가 토큰에서 꺼내 넣어 둔 회원 id다. */
    @GetMapping("/me")
    public MemberResponse me(@AuthenticationPrincipal Long memberId) {
        return MemberResponse.from(memberService.getById(memberId));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Long memberId) {
        memberService.delete(memberId);
        return ResponseEntity.noContent().build();
    }
}
