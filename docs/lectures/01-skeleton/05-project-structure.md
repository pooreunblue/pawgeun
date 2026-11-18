# 05. 디렉터리 규약과 패키지 설계

## 1. Gradle이 정한 디렉터리

```
src/
├── main/
│   ├── java/        애플리케이션 코드
│   └── resources/   설정 파일, 정적 파일 (코드가 아닌 것)
└── test/
    ├── java/        테스트 코드
    └── resources/   테스트 전용 설정
```

`java` 플러그인이 정한 **표준 규약**이다. 이 위치에 두면 별도 설정 없이 컴파일 대상으로 인식된다. 규약을 따르면 설정이 줄고, 다른 사람이 봐도 어디에 무엇이 있는지 안다.

- `main`과 `test`는 **분리된 컴파일 단위**다. 테스트 코드는 main 코드를 쓸 수 있지만 반대는 안 된다. 배포 jar에는 `main`만 들어간다.
- `resources`의 파일은 빌드 때 클래스패스에 복사된다(03강의 `application.properties`).
- 테스트 클래스 이름은 대상 클래스와 같은 패키지에 `XxxTest`로 둔다. 같은 패키지면 `package-private` 멤버도 접근할 수 있다.

## 2. 패키지 이름

```
org.example.pawgeun
└── group(org.example) + 프로젝트 이름(pawgeun)   ← 관례. 실제 패키지는 package 선언이 정한다
```

관례상 도메인을 뒤집어 쓴다(`example.org` → `org.example`). 전 세계에서 겹치지 않게 하려는 관습이다. 메인 클래스(`PawgeunApplication`)는 이 **최상위 패키지**에 둔다(02강: 스캔 범위).

## 3. 패키지를 나누는 두 가지 방식

### A. 계층별 (layer-first)

```
controller/  MemberController, PetController, AlbumController ...
service/     MemberService, PetService, AlbumService ...
repository/  MemberRepository, PetRepository ...
```

- 장점: 처음엔 단순하고 규칙이 명확하다.
- 단점: 기능 하나를 고치려면 여러 패키지를 오가야 한다. 도메인이 늘면 패키지마다 수십 개 파일이 쌓인다. 기능을 통째로 떼어 내기 어렵다.

### B. 도메인별 (domain-first) ← pawgeun의 선택

```
member/   controller, service, repository, domain, dto
auth/     controller, service, jwt, config, dto
pet/      ...
album/    ...
global/   공통 (예외, 설정, 공통 엔티티)
```

- 장점: 기능과 관련된 파일이 한곳에 모인다. 도메인 경계가 보이고 나중에 분리(모듈, 서비스)하기 쉽다.
- 단점: 도메인 사이의 의존을 신경 써야 한다.

pawgeun는 기능이 뚜렷하게 나뉜다(회원, 반려동물, 앨범, 아바타, 월드, 메시지, 대화 등). 설계 개요(`docs/design/overview.md`)의 도메인 목록이 곧 패키지 목록이다. 도메인별이 맞다.

## 4. 도메인 내부 구조와 의존 방향

한 도메인 안은 계층으로 나눈다.

```
member/
├── controller/   MemberController      HTTP를 받고 응답을 만든다
├── service/      MemberService         업무 규칙과 트랜잭션
├── repository/   MemberRepository      DB 접근
├── domain/       Member                엔티티
└── dto/          MemberResponse        요청·응답 모양
```

**의존은 한 방향으로만 흐른다.**

```
Controller → Service → Repository → (DB)
     ↘          ↘
        DTO     Entity
```

| 규칙 | 이유 |
| --- | --- |
| Controller는 Repository를 직접 부르지 않는다 | 업무 규칙과 트랜잭션이 흩어진다 |
| Service는 Controller를 모른다 (`ResponseEntity`, `HttpServletRequest` 금지) | 같은 서비스를 스케줄러(하루 메시지)에서도 쓰기 위해 |
| Entity는 Controller 밖으로 나가지 않는다 | 2단계 6강: 응답에 해시가 섞이는 사고를 막기 위해 |
| Repository는 업무 판단을 하지 않는다 | 판단은 Service가 한다 |

위반이 쌓이면 어디서 무엇이 일어나는지 추적할 수 없게 된다.

## 5. 도메인 사이의 의존

- 한 방향만 허용한다. `album`이 `pet`을 참조하는 것은 자연스럽다(앨범은 반려동물의 것). 반대로 `pet`이 `album`을 참조하면 순환이 생긴다.
- 서로 필요해 보이면 **둘 중 하나가 너무 많은 일을 맡았다**는 신호다. 공통 부분을 별도 도메인으로 빼거나 ID만 주고받는다.
- 다른 도메인의 `Repository`를 직접 부르기보다 그 도메인의 `Service`를 통한다. 규칙이 한곳에 모인다.
- 하루 메시지(`message`)는 `pet`, `avatar`, `world`, `chat`의 일부를 쓴다. 이런 **조합 로직**은 나중에 별도 서비스로 둘지 설계할 때 정한다.

## 6. global 패키지

도메인이 아닌 공통 코드다.

```
global/
├── entity/      BaseTimeEntity, JpaAuditingConfig
└── exception/   ErrorCode, BusinessException, GlobalExceptionHandler
```

**주의:** `global`, `common`, `util`은 무엇이든 던져 넣는 쓰레기통이 되기 쉽다. 기준은 "두 개 이상의 도메인이 쓰는가". 한 도메인만 쓰면 그 도메인 안에 둔다.

## 7. 코드 밖의 구조: docs/

```
docs/
├── design/       설계 (개요, ERD, API 목록). 앞으로 만들 것
├── decisions/    의사결정 기록. 왜 이렇게 만들었나
└── lectures/     강의. 개념을 어떻게 이해하나
```

세 가지는 용도가 다르다. 설계는 "무엇을 만들 것인가", 의사결정은 "왜 이렇게 했나", 강의는 "이 개념은 무엇인가"를 답한다. 한 문서에 섞지 않는다.

## 8. 파일을 어디에 둘지 판단하는 순서

1. 어떤 도메인의 일인가? → 그 도메인 패키지
2. 여러 도메인이 쓰는가? → `global`
3. 어떤 계층의 일인가? → controller / service / repository / domain / dto
4. 설정 값인가? → `resources`. 설정 클래스인가? → 해당 도메인의 `config` 또는 `global`

## 직접 해 보기

1. 3단계에서 만들 `pet` 도메인(반려동물 등록, 사진 업로드)에 필요한 클래스를 파일 이름까지 적어 보자. 어느 패키지에 두는가?
2. `AuthService`가 `MemberRepository`를 직접 쓰는 것은 의존 규칙에 맞는가? 5번 항의 "다른 도메인의 Service를 통한다"와 비교해 보고, 맞다/아니다와 대안을 생각해 보자.
3. 아무 클래스에서 `import`를 훑어 의존 방향이 위 화살표를 거스르는 곳이 있는지 찾아 보자.
4. 생각해 보기: `ErrorCode`에 `PET_NOT_FOUND`, `ALBUM_FULL` 같은 도메인 전용 오류가 계속 늘어난다. `global`에 한 enum으로 두는 것과 도메인별로 나누는 것의 장단점은?
