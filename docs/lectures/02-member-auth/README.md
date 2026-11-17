# 2단계 회원·인증 강의

가입, 로그인, JWT 인증을 만들면서 필요한 개념을 하나씩 따로 정리했다.
번호 순서대로 읽으면 "왜 이게 필요한가"가 이어진다. 각 강의 끝에 **직접 해 보기**가 있다.

| 강의 | 주제 | 이 단계 코드에서 만나는 곳 |
| --- | --- | --- |
| [01](01-authentication-basics.md) | 인증과 인가, HTTP 무상태, 세션 vs 토큰 | 전체 설계 |
| [02](02-jwt.md) | JWT 구조, 서명, 만료, 위험과 대응 | `JwtProvider` |
| [03](03-password-hashing.md) | 비밀번호 해시와 BCrypt | `MemberService.signUp`, `AuthService.login` |
| [04](04-spring-security-filter-chain.md) | Spring Security 필터 체인, SecurityContext, 401/403 | `SecurityConfig`, `JwtAuthenticationFilter` |
| [05](05-jpa-entity-repository.md) | JPA 엔티티, Repository, 유니크 제약, 트랜잭션 | `Member`, `MemberRepository`, `MemberService` |
| [06](06-dto-and-validation.md) | DTO, record, Bean Validation | `SignUpRequest`, `MemberResponse` |
| [07](07-exception-handling.md) | 예외 설계와 `@RestControllerAdvice` | `ErrorCode`, `GlobalExceptionHandler` |
| [08](08-testing.md) | 단위 테스트와 통합 테스트, MockMvc | `JwtProviderTest`, `AuthApiTest` |

요청 하나가 지나가는 길을 먼저 머리에 두고 읽으면 좋다.

```
POST /api/auth/login
  → [Security 필터 체인] JwtAuthenticationFilter (토큰 없음, 통과) → permitAll
  → AuthController.login (@Valid로 입력 검증, 06)
  → AuthService.login (BCrypt로 비밀번호 비교, 03) → JwtProvider.createAccessToken (02)
  → 응답 { accessToken, tokenType, expiresInSeconds }

GET /api/members/me   (Authorization: Bearer ...)
  → JwtAuthenticationFilter: 토큰 검증 → SecurityContext에 회원 id 저장 (04)
  → 인가 규칙 anyRequest().authenticated() 통과
  → MemberController.me (@AuthenticationPrincipal로 id 수신)
  → MemberService.getById → MemberRepository (05) → MemberResponse (06)
  → 실패하면 BusinessException → GlobalExceptionHandler가 JSON 오류로 변환 (07)
```
