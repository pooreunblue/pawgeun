package org.example.pawgeun.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.pawgeun.auth.dto.LoginRequest;
import org.example.pawgeun.auth.dto.SignUpRequest;
import org.example.pawgeun.auth.dto.TokenResponse;
import org.example.pawgeun.auth.service.AuthService;
import org.example.pawgeun.member.dto.MemberResponse;
import org.example.pawgeun.member.service.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;
    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return MemberResponse.from(
                memberService.signUp(request.email(), request.password(), request.nickname()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
