# 04. Spring Security: 필터 체인과 SecurityContext

> 이 강의 한 줄 요약: **Spring Security는 공항 보안 검색대다. 승객(요청)은 게이트(컨트롤러)에 가기 전에 검색대를 차례로 통과하고, 통과하면서 받은 "탑승 확인 도장"이 SecurityContext에 남는다.**

## 1. Spring Security가 하는 일

### 비유: 공항 검색대 줄

공항에서 비행기(컨트롤러)를 타려면 여러 관문을 **순서대로** 지난다.

```text
입구 → [신분 확인] → [보안 검색] → [출국 심사] → [탑승 게이트] → 비행기
```

Spring Security도 똑같다. 컨트롤러에 도달하기 **전에** 모든 요청을 가로채 "이 요청은 누구의 것이고, 이 주소에 접근해도 되는가"를 판단하는 서블릿 필터 묶음이다.

```text
요청 → [필터1] → [필터2] → ... → [필터N] → DispatcherServlet → Controller
         └──────── Security Filter Chain ────────┘
```

이 덕분에 컨트롤러마다 `if (로그인했나)`를 쓰지 않아도 된다. 비행기 조종사가 승객마다 여권을 검사하지 않는 것과 같다. 검사는 검색대가 다 끝내고 들여보낸다.

### 기본 설정은 "전부 막기"

의존성에 `spring-boot-starter-security`만 넣어도 기본 설정이 켜져 **모든 주소가 막힌다**. 기본 로그인 폼과 콘솔에 찍히는 임시 비밀번호도 생긴다. 공항을 막 지었을 때 모든 문이 잠겨 있는 것과 같다. 안전한 기본값이다.

우리는 직접 `SecurityFilterChain`을 정의해 "어느 문을 누구에게 열지"를 정한다.

## 2. 핵심 객체 세 개

### 비유: 탑승권, 탑승권 주머니, 주머니를 꺼내는 손

| 객체 | 역할 | 공항 비유 |
| --- | --- | --- |
| `Authentication` | 누구(principal), 자격(credentials), 권한(authorities) | **탑승권**: 이름, 좌석 등급(이코노미/비즈니스) |
| `SecurityContext` | 현재 요청의 "로그인 정보 보관함" | 승객의 **탑승권 주머니** |
| `SecurityContextHolder` | 보관함에 접근하는 통로. 요청(스레드)마다 따로 | 주머니를 꺼내는 **손**. 승객마다 자기 주머니만 꺼낸다 |

"요청(스레드)마다 따로"가 중요하다. 동시에 100명이 요청을 보내도, 각 요청은 **자기 주머니**만 본다. 철수의 요청이 영희의 탑승권을 꺼낼 일은 없다.

우리 필터가 하는 일은 결국 **주머니에 탑승권을 넣는 것**이다.

```java
// 탑승권(Authentication) 만들기
var authentication = new UsernamePasswordAuthenticationToken(
        memberId,          // principal: 회원 id (Long). "이 사람은 42번"
        null,              // credentials: 이미 토큰으로 증명됐으니 필요 없음
        List.of(new SimpleGrantedAuthority("ROLE_USER")));  // 권한: 일반 회원 등급

// 이 요청의 주머니(SecurityContext)에 탑승권을 넣는다
SecurityContextHolder.getContext().setAuthentication(authentication);
```

이 줄이 실행되면 이후 필터와 컨트롤러에서 "이 요청은 회원 42의 것"이 된다.

`credentials`를 `null`로 두는 이유: 비밀번호 같은 자격 증명은 **이미 확인이 끝났다**. 탑승권에 여권 원본까지 끼워 둘 필요는 없다. 오히려 메모리에 남아 있으면 새어 나갈 위험만 커진다.

컨트롤러는 이렇게 꺼낸다.

```java
// @AuthenticationPrincipal: 주머니 속 탑승권에서 "이름(principal)"만 꺼내 넣어 준다
public MemberResponse me(@AuthenticationPrincipal Long memberId)
```

`@AuthenticationPrincipal`은 `Authentication.getPrincipal()`을 파라미터에 넣어 준다. 우리가 principal에 `Long`을 넣었으니 `Long`으로 받는다. 넣은 타입과 받는 타입이 다르면 `null`이 들어오니 주의하자.

## 3. JwtAuthenticationFilter 읽기

### 비유: 신분 확인 창구 직원

이 필터는 공항의 **신분 확인 창구**다. 하는 일은 딱 하나, "손목 밴드(토큰)를 확인해서 탑승권을 발급해 주기"다. **누구를 비행기에 태울지는 결정하지 않는다.** 그건 뒤쪽 게이트 직원(인가 규칙)의 몫이다.

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    protected void doFilterInternal(request, response, chain) {
        // 1. Authorization 헤더를 읽는다
        String header = request.getHeader("Authorization");

        // 2. "Bearer "로 시작할 때만 토큰 확인을 시도한다
        if (header != null && header.startsWith("Bearer ")) {
            try {
                // 3. "Bearer " 7글자를 잘라 내고 토큰만 검증
                Long memberId = jwtProvider.parseMemberId(header.substring(7));
                // 4. 성공: 주머니에 탑승권을 넣는다
                SecurityContextHolder.getContext().setAuthentication(...);
            } catch (BusinessException e) {
                // 5. 실패: 응답을 끊지 않고 "가짜 밴드였음" 표시만 남긴다
                request.setAttribute(INVALID_TOKEN_ATTRIBUTE, true);
            }
        }
        // 6. 성공이든 실패든 항상 다음 관문으로 넘긴다
        chain.doFilter(request, response);
    }
}
```

**`OncePerRequestFilter`:** 서블릿은 내부 포워드나 오류 처리 때문에 한 요청이 필터를 **두 번** 지나갈 수 있다. 같은 승객이 검색대를 두 번 통과하는 셈이다. 이 부모 클래스가 "이 승객은 이미 확인했음"을 기억해 한 번만 실행되게 해 준다.

**실패해도 응답을 끊지 않는다.** 왜일까? 가짜 밴드를 찬 사람이 **"매표소(로그인)"**로 가는 길이라면 막을 이유가 없다. 매표소는 누구나 갈 수 있는 곳이니까. 토큰이 틀린 채로 `/api/auth/login`을 호출해도 그 주소는 인증이 필요 없으니 통과해야 한다.

"이 주소가 인증을 요구하는가"는 필터가 아니라 `authorizeHttpRequests` 규칙(게이트 직원)이 판단한다. 필터는 **정보만 채우고**, 판단은 뒤에 맡긴다. 역할이 깔끔하게 나뉜다.

**`chain.doFilter`를 호출하지 않으면** 요청이 거기서 멈춘다. 승객을 다음 관문으로 보내지 않고 창구 앞에 세워 둔 셈이다. 응답도 없이 멈추니 반드시 넘기는 것이 기본이다.

## 4. SecurityConfig 읽기

### 비유: 공항 운영 매뉴얼

`SecurityConfig`는 "이 공항은 이렇게 운영한다"는 매뉴얼이다.

```java
http
  .csrf(disable)                 // 쿠키 인증이 아니므로 CSRF 불필요 (01강)
  .formLogin(disable)            // 기본 로그인 폼 화면 끔 (우리는 화면 없는 API)
  .httpBasic(disable)            // 브라우저 팝업 인증 끔
  .sessionManagement(STATELESS)  // 세션을 만들지도 쓰지도 않음
  .authorizeHttpRequests(auth -> auth
      .requestMatchers("/api/auth/**").permitAll()   // 매표소(가입·로그인)는 누구나
      .requestMatchers("/error").permitAll()         // 오류 안내 창구도 누구나
      .anyRequest().authenticated())                 // 나머지는 탑승권 필요
  .exceptionHandling(...)        // 막혔을 때 안내 방송(401/403 응답) 모양 지정
  .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);  // 우리 창구를 끼워 넣을 위치
```

### 규칙 순서가 중요하다

위에서부터 **먼저 맞는 규칙**이 적용되고 나머지는 보지 않는다. 안내판을 위에서부터 읽다가 해당되는 줄에서 멈추는 것과 같다.

```java
// 잘못된 예: anyRequest 뒤에 규칙을 더 적었다
.anyRequest().authenticated()
.requestMatchers("/api/auth/**").permitAll()   // 설정 단계에서 IllegalStateException → 서버가 아예 안 뜬다
```

순서 원리대로라면 "뒤 규칙은 영영 안 쓰인다"가 되겠지만, Spring Security(6.x)는 이 실수를 아예 허용하지 않는다. `anyRequest()` 뒤에 `requestMatchers`를 적으면 요청이 들어오기도 전에, **애플리케이션 시작 단계**에서 `IllegalStateException`("Can't configure requestMatchers after anyRequest")을 던져 서버가 뜨지 않는다. 조용히 틀리는 대신 시작부터 크게 실패하게 해 둔 것이다.

그래서 `anyRequest()`는 항상 **마지막**에 둔다. "그 밖의 모든 경우"니까. 다만 `requestMatchers`끼리의 순서 실수(예: 넓은 `/api/**`를 좁은 `/api/auth/**`보다 먼저 적기)는 오류 없이 그대로 동작하므로 직접 조심해야 한다.

### addFilterBefore: 창구를 어디에 둘까

필터 체인에는 Spring Security가 정해 둔 순서가 있다. `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`는 우리 창구를 "폼 로그인 필터 자리 **앞**"에 끼워 넣는다는 뜻이다.

```text
... → [JwtAuthenticationFilter] → [UsernamePassword...Filter] → ... → [AuthorizationFilter] → 컨트롤러
         ↑ 탑승권 발급                                                    ↑ 게이트: 탑승권 확인
```

인가 판단을 하는 `AuthorizationFilter`는 훨씬 뒤에 있다. 그래서 게이트에 도착했을 때는 이미 우리가 주머니(SecurityContext)를 채워 둔 상태다. 순서가 거꾸로면 게이트 직원은 빈 주머니만 보고 모두 돌려보낼 것이다.

### STATELESS의 의미

Security가 `SecurityContext`를 세션에 저장하지 않는다. 요청이 끝나면 주머니가 **비워진다.** 다음 요청은 다시 토큰으로 채운다.

비유: 공항이 "이 승객은 아까 검사했음"이라는 기록을 남기지 않는다. 비행기를 탈 때마다 손목 밴드를 다시 보여줘야 한다. 귀찮아 보이지만, 공항이 승객 기록을 들고 있을 필요가 없어 단순하다(01강의 토큰 방식 그대로).

## 5. 401과 403을 직접 만든다

기본 응답은 빈 본문이거나 HTML이다. 앱은 HTML을 파싱할 수 없으니 JSON(`ErrorResponse`와 같은 모양)으로 바꿨다.

| 상황 | 처리기 | 응답 | 공항 비유 |
| --- | --- | --- | --- |
| 인증 정보 없음 | `AuthenticationEntryPoint` | 401 `UNAUTHORIZED` | 탑승권 없이 게이트에 옴 |
| 토큰이 있는데 잘못됨 | 같은 곳. 필터가 남긴 표시를 보고 분기 | 401 `INVALID_TOKEN` | 가짜 밴드를 차고 옴 (창구가 표시해 둠) |
| 인증은 됐으나 권한 부족 | `AccessDeniedHandler` | 403 `FORBIDDEN` | 이코노미 탑승권으로 비즈니스 라운지 진입 |

3절의 "표시만 남긴다"가 여기서 쓰인다. 창구 직원은 가짜 밴드를 봐도 승객을 쫓아내지 않고 서류에 "가짜 밴드" 메모만 붙여 보낸다. 게이트에서 막혔을 때, 안내 직원이 그 메모를 보고 "탑승권이 없으세요"가 아니라 **"밴드가 잘못됐어요"**라고 정확히 안내한다. 앱은 이 차이로 "로그인하세요"와 "토큰이 만료됐어요, 다시 로그인하세요"를 구분할 수 있다.

참고: 이 처리기들은 **필터 체인 안에서** 동작하므로 `@RestControllerAdvice`(DispatcherServlet 안의 MVC 처리 단계)가 잡아 주지 못한다. 검색대에서 생긴 문제는 비행기 안 승무원이 처리할 수 없는 것과 같다. 그래서 `HttpServletResponse`에 직접 JSON을 쓴다(07강에서 대비해서 본다).

## 6. 흔한 함정

| 증상 | 원인 | 비유 |
| --- | --- | --- |
| 로그인 성공 후에도 401 | 필터가 `SecurityContext`를 못 채움. 헤더 이름(`Authorization`)과 접두어(`Bearer ` 뒤 공백) 확인 | 밴드를 손목이 아니라 발목에 참 |
| 테스트에서 POST가 403 | CSRF가 켜져 있으면 POST가 403. 이 프로젝트는 꺼 둠 | 쓸모없는 검사대가 켜져 있음 |
| `permitAll`에 넣었는데 막힘 | 경로 오타, 또는 `/error`가 막혀서 실제 오류가 가려짐. `/error`를 열어 둔 이유 | 오류 안내 창구까지 잠겨 무슨 문제인지도 못 들음 |
| 필터가 두 번 실행 | 필터를 `@Component`로 등록하면 Security 체인 밖(일반 서블릿 필터)에서도 한 번 더 실행됨. 그래서 `SecurityConfig`에서 `new`로 만들어 체인에만 넣음 | 같은 창구를 공항 입구와 검색대 안에 두 번 설치 |

## 직접 해 보기

1. `SecurityConfig`에서 `.requestMatchers("/api/auth/**").permitAll()` 줄을 지우고 로그인을 호출해 보자. 어떤 응답이 오는가? 왜 그런가? (매표소에 가려면 탑승권이 필요한 공항이 된다.)
2. `JwtAuthenticationFilter`에 `System.out.println`을 넣어 요청마다 몇 번 호출되는지 보자. 로그인 요청에서도 호출되는가?
3. `MemberController`에 `@PreAuthorize`나 `hasRole("ADMIN")` 규칙을 하나 추가해 보면 403이 어떻게 나오는지 확인해 보자.
4. 생각해 보기: 필터 안에서 매 요청마다 DB로 회원을 조회하면 무엇이 좋고 무엇이 나쁜가? (지금은 컨트롤러 단계에서 조회한다.)
