# 08. 테스트: 단위 테스트와 통합 테스트

## 1. 두 종류를 나눠 쓴다

| | 단위 테스트 | 통합 테스트 |
| --- | --- | --- |
| 대상 | 클래스 하나 | 여러 계층을 실제로 연결 |
| 스프링 | 안 띄움 | 띄움 |
| 속도 | 밀리초 | 수 초 |
| 이 프로젝트 | `JwtProviderTest` | `AuthApiTest` |
| 잡아내는 것 | 로직 오류 | 설정·연결 오류 (필터 순서, 빈 등록, SQL) |

규칙은 이렇다. **로직이 복잡한 곳은 단위 테스트로 촘촘히, 큰 흐름은 통합 테스트로 몇 개만.** 전부 통합 테스트로 쓰면 느려져서 아무도 안 돌린다.

## 2. 단위 테스트: JwtProviderTest

`JwtProvider`는 스프링 없이 `new`로 만들 수 있다. 의존하는 것이 `JwtProperties` 값 하나뿐이어서다. 이것이 생성자 주입의 이점이다. 필드 주입이었다면 스프링 없이 만들기 어렵다.

테스트 이름을 한글 문장으로 써서 **무엇을 보장하는지**가 이름에 드러나게 했다.

```java
@Test
void 만료된_토큰은_거부한다() {
    String expired = Jwts.builder()....expiration(과거 시각)....compact();
    assertThatThrownBy(() -> provider.parseMemberId(expired))
        .isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
}
```

**시간을 기다리지 않는다.** 30분을 기다릴 수 없으니 만료 시각이 이미 지난 토큰을 직접 만들어 넣었다. 테스트하기 어려운 것은 입력을 직접 만들어 주면 된다.

여섯 개가 다루는 것: 정상 왕복, 만료, 다른 키, 내용 변조, 쓰레기 문자열, 약한 키. **정상 경로보다 실패 경로**가 보안 테스트의 중심이다.

## 3. 통합 테스트: AuthApiTest

```java
@SpringBootTest        // 애플리케이션 전체를 띄움
@AutoConfigureMockMvc  // 가짜 HTTP 클라이언트(MockMvc) 준비
class AuthApiTest { ... }
```

`MockMvc`는 실제 네트워크 없이 서블릿 환경에 요청을 던진다. **Security 필터 체인을 포함해** 통과한다. 그래서 401/403, 토큰 필터까지 검증된다.

```java
mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.email").value("a@example.com"));
```

- `jsonPath("$.email")`: 응답 JSON에서 `email` 값을 꺼내 비교.
- `.doesNotExist()`: 있으면 안 되는 필드(비밀번호)를 확인. "빠져야 할 것"도 테스트한다.
- `@BeforeEach`에서 `memberRepository.deleteAll()`로 매 테스트를 **깨끗한 상태**에서 시작한다. 테스트끼리 서로 영향을 주지 않아야 하고, 실행 순서에 의존하면 안 된다.

### 테스트용 DB

`src/test/resources/application.properties`가 운영 설정을 덮어쓴다.

```
spring.datasource.url=jdbc:h2:mem:pawse;MODE=PostgreSQL   # 인메모리 H2
spring.docker.compose.enabled=false                         # 테스트에서 Docker 안 띄움
pawse.jwt.secret=test-...                                   # 테스트 전용 키
```

장점: 빠르고 Docker가 필요 없다. 단점: H2는 PostgreSQL이 아니다. 운영 DB 고유 기능(JSON 컬럼, 특정 쿼리)을 쓰기 시작하면 Testcontainers로 진짜 PostgreSQL을 띄우는 쪽으로 옮긴다. 결정 근거는 `docs/decisions/member-auth.md`에 있다.

## 4. 좋은 테스트의 기준

1. **하나의 이유로만 실패한다.** 한 테스트는 한 가지를 확인한다.
2. **이름이 명세다.** 실패했을 때 이름만 보고 무엇이 깨졌는지 안다.
3. **독립적이다.** 순서, 외부 상태에 의존하지 않는다.
4. **실패 경로를 포함한다.** 성공만 있는 테스트는 절반이다.
5. **구현이 아니라 동작을 검증한다.** 내부 메서드 호출 여부보다 응답과 DB 결과를 본다.

## 5. 이번 단계에서 테스트가 잡아 준 것

`AuthApiTest.탈퇴하면...`은 "탈퇴한 회원의 토큰으로 `/me`를 호출하면 404"를 고정한다. 토큰은 서명이 유효해도 회원이 없을 수 있다는 02강의 약점이, 코드로 확인되는 동작이 된다.

## 직접 해 보기

1. `./gradlew test`를 실행하고 `build/reports/tests/test/index.html`을 열어 결과를 보자.
2. `AuthService.login`의 `matches` 조건에서 `!`를 지워 테스트를 돌려 보자. 어떤 테스트가 먼저 깨지고 그 이름만으로 원인을 알 수 있는가? (확인 후 원복)
3. 이메일 정규화(`toLowerCase`) 한 줄을 지우면 어떤 테스트가 실패하는가? 안 깨진다면 그 동작을 지킬 테스트를 하나 추가해 보자.
4. `JwtProviderTest`에 "서로 다른 회원 id로 만든 토큰은 서로 다른 id를 돌려준다" 테스트를 추가해 보자.
5. 생각해 보기: `AuthApiTest`가 H2로 통과한 것이 PostgreSQL에서의 통과를 보장하는가?
