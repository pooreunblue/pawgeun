# 03. 설정 파일과 우선순위

## 1. 설정이 필요한 이유

같은 코드가 내 컴퓨터, 테스트, 운영 서버에서 다른 값(DB 주소, 비밀키, 포트)으로 돌아야 한다. 값을 코드에 박으면 환경마다 코드를 고쳐야 한다. 그래서 **코드와 설정을 분리**한다. Django의 `settings.py`에 해당한다.

## 2. application.properties

```properties
spring.application.name=pawgeun

# 로컬 개발: compose.yaml의 PostgreSQL을 spring-boot-docker-compose가 자동으로 띄우고 연결한다.
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false
```

`키=값` 한 줄씩이다. `#`은 주석. 키는 점으로 계층을 표현한다. 같은 내용을 YAML(`application.yml`)로도 쓸 수 있다.

```yaml
spring:
  jpa:
    open-in-view: false
```

둘은 기능이 같고 취향 차이다. 이 프로젝트는 한 줄에 한 값이라 단순한 properties를 쓴다. 한 파일에 둘을 섞지 않는다.

### 각 설정의 의미

| 키 | 값 | 의미 |
| --- | --- | --- |
| `spring.application.name` | `pawgeun` | 애플리케이션 이름. 로그와 모니터링에서 식별용 |
| `spring.jpa.hibernate.ddl-auto` | `update` | 엔티티를 보고 테이블을 자동으로 맞춘다. 개발용이고, 운영 전에 Flyway로 교체 (2단계 5강) |
| `spring.jpa.open-in-view` | `false` | 트랜잭션이 끝난 뒤 컨트롤러 단계에서 지연 로딩이 DB를 건드리는 것을 막음. API 서버에서는 끈다 |

`ddl-auto` 값의 종류: `none`(아무것도 안 함), `validate`(맞는지 검사만), `update`(차이를 반영), `create`(시작 때 새로 만듦), `create-drop`(시작에 만들고 종료에 삭제). 테스트는 `create-drop`, 운영은 `validate` 또는 `none`이 정석이다.

DB 접속 정보(`spring.datasource.url` 등)가 **없다는 점**에 주목하자. 04강의 docker-compose 자동 연결이 대신 채운다.

## 3. 설정이 정해지는 순서 (우선순위)

같은 키가 여러 곳에 있으면 **위쪽이 이긴다.**

```
1. 명령행 인자           java -jar app.jar --server.port=9090
2. OS 환경변수           SERVER_PORT=9090
3. 외부 설정 파일         실행한 현재 디렉터리(또는 그 아래 config/)의 application.properties. jar가 있는 폴더가 아니다
4. 클래스패스 설정 파일    src/main/resources/application.properties  (jar 안)
5. 코드의 기본값
```

이 구조 덕분에 **jar를 다시 빌드하지 않고** 환경마다 값을 바꿀 수 있다. 운영 서버에서는 환경변수로 덮어쓰는 것이 일반적이다.

### 환경변수 이름 규칙 (Relaxed Binding)

점은 밑줄로 바꾸고, 하이픈은 **제거**하고, 모두 대문자로 쓴다.

| properties 키 | 환경변수 |
| --- | --- |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` |
| `spring.jpa.hibernate.ddl-auto` | `SPRING_JPA_HIBERNATE_DDLAUTO` |
| `pawgeun.jwt.access-token-validity` | `PAWGEUN_JWT_ACCESSTOKENVALIDITY` |

### 플레이스홀더와 기본값

```properties
pawgeun.jwt.secret=${JWT_SECRET:local-dev-only-secret-...}
```

`${이름:기본값}`은 "환경변수 `JWT_SECRET`이 있으면 그것을, 없으면 콜론 뒤 기본값을" 쓴다. 로컬에서는 바로 돌고 운영에서는 환경변수로 주입하게 하는 패턴이다. (2단계에서 사용)

## 4. 프로필

환경별 설정 파일을 나눌 수 있다.

```
application.properties          공통
application-local.properties    spring.profiles.active=local 일 때 추가로 적용
application-prod.properties     spring.profiles.active=prod 일 때 추가로 적용
```

공통 파일을 먼저 읽고, 활성 프로필 파일이 같은 키를 덮어쓴다. 아직 프로필을 쓰지 않는다(환경이 로컬 하나뿐이다). 배포를 시작하는 때 `prod` 프로필을 추가한다.

## 5. 설정값을 코드에서 쓰는 방법

| 방법 | 예 | 언제 |
| --- | --- | --- |
| `@Value("${키}")` | 값 하나 | 한두 개를 잠깐 쓸 때 |
| `@ConfigurationProperties` | `JwtProperties` 레코드 | 관련 값 묶음. 타입 변환(`30m` → `Duration`)과 검증이 된다 (2단계) |
| `Environment` | 코드에서 동적으로 조회 | 드문 경우 |

값이 여러 개이거나 의미 있는 묶음이면 `@ConfigurationProperties`가 낫다. 오타를 컴파일 단계에서 잡고 IDE 자동완성도 된다.

## 6. 테스트 설정: 덮어쓰기가 아니라 대체

```properties
# src/test/resources/application.properties
spring.datasource.url=jdbc:h2:mem:pawgeun;MODE=PostgreSQL
spring.jpa.hibernate.ddl-auto=create-drop
spring.docker.compose.enabled=false
```

테스트를 실행하면 클래스패스에 `application.properties`가 두 개(main, test) 있다. 그런데 **같은 이름의 파일은 먼저 발견된 하나만 읽힌다.** 테스트 실행 때는 test 리소스가 앞서므로 main의 `application.properties`는 **읽히지 않는다.** 즉 "main 설정에 test 설정을 덧씌우는" 것이 아니라 "통째로 바꿔치는" 것이다.

이 사실이 중요한 이유:

- main에 있는 값 중 테스트에도 필요한 것은 test 파일에 **다시 써야 한다.** 2단계의 `pawgeun.jwt.secret`을 test 파일에도 적어 둔 이유다. 빠지면 테스트가 "값이 없다"며 실패한다.
- 반대로 main에만 있는 `open-in-view=false`는 테스트에 적용되지 않는다. 설정 차이가 테스트와 운영의 동작 차이를 만들 수 있다.

테스트 설정의 의도는 이렇다.

- H2 인메모리 DB(`MODE=PostgreSQL`로 PostgreSQL 문법에 가깝게): Docker 없이 빠르게 돈다.
- `create-drop`: 컨텍스트가 시작될 때 스키마를 만들고 종료될 때 지운다. 스프링 테스트는 컨텍스트를 여러 테스트가 공유하므로 테스트마다 깨끗한 데이터가 보장되지는 않는다. 필요하면 `@BeforeEach` 정리나 `@Transactional` 롤백을 따로 쓴다.
- `docker.compose.enabled=false`: 테스트에서 컨테이너를 띄우지 않는다. (Spring Boot는 기본적으로 테스트에서 compose를 건너뛰지만, 의도를 드러내려고 명시했다.)

## 7. 비밀값 다루기

- 비밀번호, API 키, JWT 비밀키는 **저장소에 커밋하지 않는다.** 한번 올라가면 지워도 Git 이력에 남는다.
- `compose.yaml`의 DB 비밀번호(`pawgeun`)는 내 컴퓨터에서만 쓰는 개발용이라 괜찮다. 포트도 `127.0.0.1`에만 열어(04강) 같은 네트워크의 다른 기기가 접속하지 못한다. 운영 DB 비밀번호는 환경변수나 비밀 저장소로 주입한다.
- Gemini, Claude API 키(이후 단계)도 같은 방식으로 환경변수에서 읽는다.

## 직접 해 보기

1. `./gradlew bootRun --args='--server.port=9090'`으로 띄워 보자. 같은 값을 환경변수(`SERVER_PORT=9090 ./gradlew bootRun`)로 줘도 되는지 확인해 보자.
2. `application.properties`에 `server.port=8081`을 넣고, 명령행으로 `--server.port=9090`을 같이 주면 어느 쪽 포트로 뜨는가?
3. `src/test/resources/application.properties`에서 `pawgeun.jwt.secret` 줄을 지우고 `./gradlew test`를 해 보자(2단계 코드가 있을 때). 어떤 오류가 나는가? (확인 후 원복)
4. `spring.jpa.show-sql=true`를 켜면 어떤 로그가 늘어나는가? 이 설정은 main에 넣어야 하는가, test에 넣어야 하는가?
5. 생각해 보기: 팀원이 로컬에서만 쓰고 싶은 값(개인 API 키)은 어디에 두는 것이 좋을까? (힌트: 환경변수, 또는 Git에 올리지 않는 `application-local.properties`)
