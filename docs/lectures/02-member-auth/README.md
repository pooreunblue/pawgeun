# 2단계 회원·인증 강의

가입, 로그인, JWT 인증을 만들면서 필요한 개념을 하나씩 따로 정리했다.
번호 순서대로 읽으면 "왜 이게 필요한가"가 이어진다. 각 강의는 **비유로 먼저 감을 잡고 → 코드로 확인**하는 순서로 썼고, 끝에 **직접 해 보기**가 있다.

| 강의 | 주제 | 한 줄 비유 | 이 단계 코드에서 만나는 곳 |
| --- | --- | --- | --- |
| [01](01-authentication-basics.md) | 인증과 인가, HTTP 무상태, 세션 vs 토큰 | 기억상실 창구 직원, 번호표 vs 손목 밴드 | 전체 설계 |
| [02](02-jwt.md) | JWT 구조, 서명, 만료, 위험과 대응 | 누구나 읽는 엽서 + 위조 불가 밀랍 도장 | `JwtProvider` |
| [03](03-password-hashing.md) | 비밀번호 해시와 BCrypt | 되돌릴 수 없는 믹서기 + 사람마다 다른 소금 | `MemberService.signUp`, `AuthService.login` |
| [04](04-spring-security-filter-chain.md) | Spring Security 필터 체인, SecurityContext, 401/403 | 공항 검색대와 탑승권 주머니 | `SecurityConfig`, `JwtAuthenticationFilter` |
| [05](05-jpa-entity-repository.md) | JPA 엔티티, Repository, 유니크 제약, 트랜잭션 | 객체↔표 통역사, 극장 좌석, 계좌 이체 | `Member`, `MemberRepository`, `MemberService` |
| [06](06-dto-and-validation.md) | DTO, record, Bean Validation | 진료 기록 원본 vs 진단서, 신청서 기재 요령 | `SignUpRequest`, `MemberResponse` |
| [07](07-exception-handling.md) | 예외 설계와 `@RestControllerAdvice` | 본사 고객센터 하나로 모으기 | `ErrorCode`, `GlobalExceptionHandler` |
| [08](08-testing.md) | 단위 테스트와 통합 테스트, MockMvc | 부품 검사 vs 시운전 | `JwtProviderTest`, `AuthApiTest` |

요청 하나가 지나가는 길을 먼저 머리에 두고 읽으면 좋다.

```text
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

같은 길을 공항 비유로 다시 보면 이렇다.

```text
로그인  = 매표소: 누구나 갈 수 있다(permitAll). 신분 확인(비밀번호) 후 손목 밴드(JWT)를 채워 준다.
/me 요청 = 탑승: 검색대(필터)에서 밴드를 확인해 탑승권 주머니(SecurityContext)에 넣고,
          게이트(인가 규칙)가 주머니를 보고 통과시킨다. 기내(컨트롤러)에서는 주머니 속 이름만 꺼내 쓴다.
문제 발생 = 검색대 문제는 검색대가 직접 안내(EntryPoint), 기내 문제는 고객센터(@RestControllerAdvice)가 안내.
          둘 다 같은 안내문 양식(ErrorCode)을 쓴다.
```
