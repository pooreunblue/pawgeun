# 02. Spring Boot가 뜨는 과정: 컨테이너, DI, 자동 구성

## 1. 시작점은 main 하나

```java
@SpringBootApplication
public class PawgeunApplication {
    public static void main(String[] args) {
        SpringApplication.run(PawgeunApplication.class, args);
    }
}
```

일반 자바 프로그램과 똑같이 `main`에서 시작한다. `SpringApplication.run` 한 줄이 아래 일을 모두 한다.

```
1. 설정 읽기 (application.properties, 환경변수, 명령행 인자)            → 03강
2. ApplicationContext(스프링 컨테이너) 생성
3. 클래스들을 훑어 빈(Bean)으로 등록하고 서로 연결(DI)
4. 자동 구성: classpath를 보고 필요한 설정을 알아서 추가
5. 내장 Tomcat 시작 → 8080 포트에서 요청 대기
6. "Started PawgeunApplication in 3.1 seconds" 로그 출력
```

Django의 `manage.py runserver`가 설정을 읽고 앱을 로드한 뒤 서버를 켜는 것과 같은 흐름이지만, 자바에서는 서버(Tomcat)가 애플리케이션 **안에** 들어 있다. 별도 서버에 올리지 않고 `java -jar`로 실행한다.

## 2. 스프링 컨테이너와 빈, DI

### 문제: 객체끼리 연결하는 일

`AuthService`는 `MemberRepository`, `PasswordEncoder`, `JwtProvider`가 필요하다. 직접 만들면 이렇게 된다.

```java
// 직접 만드는 방식: 모든 곳에서 연결 코드를 반복하고, 바꾸기 어렵다
AuthService s = new AuthService(new MemberRepositoryImpl(...), new BCryptPasswordEncoder(), new JwtProvider(...));
```

### 해법: 컨테이너가 만들고 연결한다

스프링 컨테이너(`ApplicationContext`)가 객체(**빈**)를 만들어 보관하고, 필요한 곳에 넣어 준다. 이것이 **IoC**(제어의 역전)와 **DI**(의존성 주입)다. "내가 만들어서 쓴다"에서 "필요하다고 선언하면 받는다"로 바뀐다.

```java
@Service
@RequiredArgsConstructor           // final 필드를 받는 생성자를 Lombok이 만들어 줌
public class AuthService {
    private final MemberRepository memberRepository;   // 컨테이너가 넣어 줌
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
}
```

### 빈이 되는 방법 두 가지

| 방법 | 예 | 언제 |
| --- | --- | --- |
| 클래스에 어노테이션 | `@Component`, `@Service`, `@Repository`, `@RestController` | 내가 만든 클래스 |
| `@Bean` 메서드 | `SecurityConfig`의 `passwordEncoder()` | 남이 만든 클래스(`BCryptPasswordEncoder`)를 등록할 때 |

`@Service`, `@Repository`, `@RestController`는 모두 `@Component`의 특수화다. 역할을 이름으로 드러내고 몇 가지 부가 기능이 붙는다.

### 생성자 주입을 쓰는 이유

필드에 `@Autowired`를 붙이는 방식 대신 **생성자 주입**을 쓴다.

1. 필드를 `final`로 둘 수 있어 한 번 주입되면 바뀌지 않는다.
2. 스프링 없이도 `new AuthService(a, b, c)`로 만들어 단위 테스트할 수 있다 (`JwtProviderTest`가 그 예).
3. 필요한 의존성이 빠지면 컴파일·기동 시점에 바로 드러난다.
4. 의존성이 너무 많아지면 생성자가 길어져서 "이 클래스가 일을 너무 많이 한다"는 신호가 된다.

빈은 기본적으로 **싱글톤**이다. 컨테이너 안에 하나만 만들어 모두가 공유한다. 그래서 빈에는 요청마다 달라지는 값을 필드에 저장하면 안 된다(여러 요청이 동시에 같은 객체를 쓴다).

## 3. @SpringBootApplication 해부

```java
@SpringBootApplication
= @SpringBootConfiguration   // 이 클래스는 설정 클래스다 (@Bean을 쓸 수 있다)
+ @EnableAutoConfiguration   // 자동 구성을 켠다
+ @ComponentScan             // 이 클래스의 패키지 이하를 훑어 빈을 찾는다
```

### 컴포넌트 스캔과 메인 클래스의 위치

`@ComponentScan`은 **메인 클래스가 있는 패키지와 그 하위**만 훑는다. 메인 클래스가 `org.example.pawgeun`에 있으므로 `org.example.pawgeun.member...`, `org.example.pawgeun.auth...`는 자동으로 잡힌다. 그러나 `org.example.other` 패키지의 `@Service`는 못 찾는다. 그래서 **메인 클래스는 항상 최상위 패키지에 둔다.** 이 규칙을 모르면 "빈이 없다"는 오류에서 한참 헤맨다.

## 4. 자동 구성: 마법의 정체

의존성을 넣었을 뿐인데 `DataSource`, `EntityManagerFactory`, `DispatcherServlet`, `ObjectMapper` 같은 빈이 저절로 생겼다. 이것이 **자동 구성(auto-configuration)**이다.

원리는 조건부 등록이다. Spring Boot 안에는 "만약 ~가 있으면 ~를 등록한다"는 설정 클래스가 수백 개 들어 있다.

```
"classpath에 DataSource 클래스와 JPA가 있고,
 사용자가 직접 정의한 EntityManagerFactory 빈이 없다면
 → 기본 EntityManagerFactory를 만들어 등록한다"
```

조건 어노테이션이 이 판단을 한다.

| 조건 | 뜻 |
| --- | --- |
| `@ConditionalOnClass` | 특정 클래스가 classpath에 있을 때 |
| `@ConditionalOnMissingBean` | 사용자가 직접 같은 빈을 안 만들었을 때 |
| `@ConditionalOnProperty` | 설정값이 특정 값일 때 |

**`@ConditionalOnMissingBean` 덕분에 내가 정의하면 내 것이 우선한다.** 2단계에서 `SecurityConfig`가 `SecurityFilterChain` 빈을 직접 만들자 Spring Security의 기본 필터 체인(모든 주소 차단, 기본 로그인 폼)이 물러난 것이 이 원리다.

그래서 의존성이 곧 기능이다.

| 넣은 의존성 | 자동으로 생기는 것 |
| --- | --- |
| `starter-webmvc` | 내장 Tomcat, DispatcherServlet, JSON 변환 |
| `starter-data-jpa` + PostgreSQL 드라이버 | DataSource(Hikari 커넥션 풀), EntityManagerFactory, 트랜잭션 매니저 |
| `starter-security`(2단계) | 보안 필터 체인 기본값 |

### 무엇이 등록되고 안 됐는지 보는 법

```bash
./gradlew bootRun --args='--debug'
```

로그에 **CONDITIONS EVALUATION REPORT**가 나온다. `Positive matches`(적용된 것)와 `Negative matches`(왜 적용 안 됐는지)가 나열된다. 자동 구성이 이상할 때 가장 먼저 보는 곳이다.

## 5. 요청이 컨트롤러에 닿기까지

```
HTTP 요청
  → 내장 Tomcat
  → 서블릿 필터들 (Spring Security 필터 체인 등)       ← 2단계 04강
  → DispatcherServlet                                   (모든 요청의 관문)
      → HandlerMapping: 주소·메서드에 맞는 컨트롤러 메서드 찾기
      → 인자 변환: JSON → DTO, @Valid 검증                ← 2단계 06강
      → 컨트롤러 메서드 실행
      → 반환값 변환: 객체 → JSON
  → 응답
```

Django의 `urls.py` → `view` 흐름에 대응한다. 다만 URL 매핑을 별도 파일이 아니라 `@GetMapping`, `@PostMapping` 어노테이션으로 메서드 옆에 쓴다.

## 6. 기동 로그 읽기

```
Starting PawgeunApplication using Java 21 ...
Bootstrapping Spring Data JPA repositories ...      ← JPA 레포지토리 인식
Found 1 JPA repository interface                    ← MemberRepository
HikariPool-1 - Start completed                      ← DB 연결 풀 준비
Tomcat started on port 8080                         ← 서버 시작
Started PawgeunApplication in 3.1 seconds
```

오류가 나면 **맨 아래 `Caused by:`** 를 읽는다. 위쪽은 스택이 쌓이는 과정이고 진짜 원인은 가장 아래에 있다.

| 오류 메시지 | 대개의 원인 |
| --- | --- |
| `Failed to configure a DataSource: 'url' attribute is not specified` | DB 접속 정보가 없음. `java -jar`로 실행했거나(compose 지원은 jar에 없음) compose 자동 연결이 동작하지 않음 (04강) |
| Docker 연결 오류 (daemon에 접속 불가 등) | Docker가 꺼져 있음. DataSource 오류보다 먼저 난다 |
| `Port 8080 was already in use` | 이미 다른 서버가 8080을 쓰는 중. 이전 실행을 종료하거나 `--server.port=8081` |
| `No qualifying bean of type ...` | 빈이 없음. `@Service` 누락 또는 스캔 범위 밖 패키지 |
| `UnsatisfiedDependencyException` | 생성자가 요구하는 빈을 못 만듦. 아래 `Caused by:` 확인 |
| `Circular dependency` | 빈 A와 B가 서로를 요구함. 설계를 고쳐야 함 |

## 7. 테스트와 컨텍스트

```java
@SpringBootTest
class PawgeunApplicationTests {
    @Test
    void contextLoads() { }
}
```

본문이 비어 있는데도 의미가 있다. `@SpringBootTest`는 **스프링 컨텍스트 전체를 실제로 띄운다.** 빈 연결, 설정, DB 연결 중 하나라도 깨지면 이 테스트가 실패한다. 가장 싼 "전체가 기동은 되는가" 점검(스모크 테스트)이다. CI에서 이 테스트 하나가 설정 실수를 많이 잡아 준다.

컨텍스트 기동은 느려서(수 초) 같은 설정이면 테스트 사이에 **재사용(캐시)**한다. 그래서 통합 테스트는 가능하면 같은 설정으로 몰아 쓰는 것이 빠르다.

## 직접 해 보기

1. `./gradlew bootRun --args='--debug'`(Docker 실행 상태)로 띄워 `DataSourceAutoConfiguration`이 Positive matches에 있는지 찾아 보자.
2. 메인 클래스를 `org.example.pawgeun.config` 같은 하위 패키지로 옮기면 어떤 오류가 나는가? (확인 후 원복)
3. 아무 `@Service` 클래스에서 `@Service`를 지우고 기동해 보자. 어떤 오류가 나는가? 오류 메시지 맨 아래 `Caused by:`를 찾아 보자.
4. `@SpringBootTest` 테스트에 `@Autowired ApplicationContext ctx;`를 받아 `ctx.getBeanDefinitionNames()`를 출력해 보자. 내가 만들지 않은 빈이 몇 개나 있는가?
5. 생각해 보기: `AuthService`가 `MemberService`를 쓰고 `MemberService`가 `AuthService`를 쓰면 어떻게 되는가? 어떻게 설계를 바꾸면 되는가?
