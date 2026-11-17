# 04. Spring Security: 필터 체인과 SecurityContext

## 1. Spring Security가 하는 일

컨트롤러에 도달하기 **전에** 모든 요청을 가로채 "이 요청은 누구의 것이고, 이 주소에 접근해도 되는가"를 판단하는 서블릿 필터 묶음이다. 컨트롤러마다 `if (로그인했나)`를 쓰지 않아도 되는 이유다.

```
요청 → [필터1] → [필터2] → ... → [필터N] → DispatcherServlet → Controller
         └──────── Security Filter Chain ────────┘
```

의존성에 `spring-boot-starter-security`만 넣어도 기본 설정이 켜져 **모든 주소가 막힌다**(기본 로그인 폼과 임시 비밀번호가 생긴다). 그래서 직접 `SecurityFilterChain`을 정의해 기본 동작을 대체한다.

## 2. 핵심 객체 세 개

| 객체 | 역할 |
| --- | --- |
| `SecurityContext` | 현재 요청의 "로그인 정보 보관함" |
| `Authentication` | 보관함에 들어가는 내용. 누구(principal), 자격(credentials), 권한(authorities) |
| `SecurityContextHolder` | 보관함에 접근하는 통로. 요청(스레드)마다 따로 있다 |

우리 필터가 하는 일은 결국 이것이다.

```java
var authentication = new UsernamePasswordAuthenticationToken(
        memberId,          // principal: 회원 id (Long)
        null,              // credentials: 이미 토큰으로 증명됐으니 필요 없음
        List.of(new SimpleGrantedAuthority("ROLE_USER")));  // 권한
SecurityContextHolder.getContext().setAuthentication(authentication);
```

이 한 줄이 실행되면 이후 필터와 컨트롤러에서 "이 요청은 회원 42의 것"이 된다. 컨트롤러는 이렇게 꺼낸다.

```java
public MemberResponse me(@AuthenticationPrincipal Long memberId)
```

`@AuthenticationPrincipal`은 `Authentication.getPrincipal()`을 파라미터에 넣어 준다. 우리가 principal에 `Long`을 넣었으니 `Long`으로 받는다.

## 3. JwtAuthenticationFilter 읽기

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    protected void doFilterInternal(request, response, chain) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Long memberId = jwtProvider.parseMemberId(header.substring(7));
                SecurityContextHolder.getContext().setAuthentication(...);
            } catch (BusinessException e) {
                request.setAttribute(INVALID_TOKEN_ATTRIBUTE, true);   // 표시만
            }
        }
        chain.doFilter(request, response);   // 항상 다음으로 넘긴다
    }
}
```

- `OncePerRequestFilter`: 한 요청에 필터가 두 번 실행되는 경우(포워드 등)를 막아 준다.
- **실패해도 응답을 끊지 않는다.** 토큰이 틀린 채로 `/api/auth/login`을 호출해도 그 주소는 인증이 필요 없으니 통과해야 한다. "이 주소가 인증을 요구하는가"는 필터가 아니라 `authorizeHttpRequests` 규칙이 판단한다.
- `chain.doFilter`를 호출하지 않으면 요청이 거기서 멈춘다. 반드시 넘기는 것이 기본이다.

## 4. SecurityConfig 읽기

```java
http
  .csrf(disable)                 // 쿠키 인증이 아니므로 CSRF 불필요 (01강)
  .formLogin(disable)            // 기본 로그인 폼 끔
  .httpBasic(disable)            // 브라우저 팝업 인증 끔
  .sessionManagement(STATELESS)  // 세션을 만들지도 쓰지도 않음
  .authorizeHttpRequests(auth -> auth
      .requestMatchers("/api/auth/**").permitAll()   // 가입·로그인은 누구나
      .requestMatchers("/error").permitAll()
      .anyRequest().authenticated())                 // 나머지는 로그인 필요
  .exceptionHandling(...)        // 401/403 응답 모양 지정
  .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
```

**규칙 순서가 중요하다.** 위에서부터 먼저 맞는 규칙이 적용된다. `anyRequest()`는 항상 마지막에 둔다.

**`addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`:** 필터 체인에는 정해진 순서가 있다. 우리 필터를 "폼 로그인 필터 자리 앞"에 끼워 넣는다는 뜻이다. 인가 판단(`AuthorizationFilter`)은 그보다 뒤에 있으므로, 인가 판단 시점에는 이미 우리가 `SecurityContext`를 채워 둔 상태가 된다.

**STATELESS의 의미:** Security가 `SecurityContext`를 세션에 저장하지 않는다. 요청이 끝나면 보관함이 비워지고, 다음 요청은 다시 토큰으로 채운다. 매 요청이 독립적이다(01강의 토큰 방식 그대로).

## 5. 401과 403을 직접 만든다

기본 응답은 빈 본문이거나 HTML이다. 앱이 파싱할 수 있도록 JSON(`ErrorResponse`와 같은 모양)으로 바꿨다.

| 상황 | 처리기 | 응답 |
| --- | --- | --- |
| 인증 정보 없음 | `AuthenticationEntryPoint` | 401 `UNAUTHORIZED` |
| 토큰이 있는데 잘못됨 | 같은 곳. 필터가 남긴 표시를 보고 분기 | 401 `INVALID_TOKEN` |
| 인증은 됐으나 권한 부족 | `AccessDeniedHandler` | 403 `FORBIDDEN` |

참고: 이 처리기들은 **필터 체인 안에서** 동작하므로 `@RestControllerAdvice`(컨트롤러 이후 단계)가 잡아 주지 못한다. 그래서 `HttpServletResponse`에 직접 쓴다. (07강에서 대비해서 본다.)

## 6. 흔한 함정

- 로그인 성공 후에도 401이 나온다 → 필터가 `SecurityContext`를 못 채웠다. 헤더 이름(`Authorization`)과 접두어(`Bearer ` 뒤 공백)를 확인.
- 테스트에서 403이 난다 → CSRF가 켜져 있으면 POST가 403이다. 이 프로젝트는 꺼 두었다.
- `permitAll`에 넣었는데 막힌다 → 경로 오타, 또는 `/error`가 막혀서 실제 오류가 가려진 경우다. `/error`를 열어 둔 이유다.
- 필터를 `@Component`로 등록하면 Security 체인 밖에서도 한 번 더 실행될 수 있다. 그래서 `SecurityConfig`에서 `new`로 만들어 체인에만 넣었다.

## 직접 해 보기

1. `SecurityConfig`에서 `.requestMatchers("/api/auth/**").permitAll()` 줄을 지우고 로그인을 호출해 보자. 어떤 응답이 오는가? 왜 그런가?
2. `JwtAuthenticationFilter`에 `System.out.println`을 넣어 요청마다 몇 번 호출되는지 보자. 로그인 요청에서도 호출되는가?
3. `MemberController`에 `@PreAuthorize`나 `hasRole("ADMIN")` 규칙을 하나 추가해 보면 403이 어떻게 나오는지 확인해 보자.
4. 생각해 보기: 필터 안에서 매 요청마다 DB로 회원을 조회하면 무엇이 좋고 무엇이 나쁜가? (지금은 컨트롤러 단계에서 조회한다.)
