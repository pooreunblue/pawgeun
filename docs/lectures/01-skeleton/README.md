# 1단계 뼈대 강의

프로젝트를 처음 만들 때 이미 들어 있는 파일들이 각각 무엇이고 왜 있는지를 하나씩 풀어 쓴 강의다.
2단계(회원·인증)는 이 뼈대 위에 쌓였으므로, 먼저 읽으면 뒤가 쉬워진다. 각 강의 끝에 **직접 해 보기**가 있다.

> 프로젝트 이름은 처음에 `pawse`였다가 `pawfect`를 거쳐 `pawgeun`으로 바뀌었다(회원·인증 PR에 포함). 강의는 바뀐 이름 기준으로 쓴다.

| 강의 | 주제 | 다루는 파일 |
| --- | --- | --- |
| [01](01-gradle.md) | Gradle: 빌드 도구, 의존성, wrapper | `build.gradle`, `settings.gradle`, `gradlew`, `gradle/wrapper/` |
| [02](02-spring-boot-startup.md) | Spring Boot가 뜨는 과정: 자동 구성, 컨테이너, DI | `PawgeunApplication.java`, `PawgeunApplicationTests.java` |
| [03](03-configuration.md) | 설정 파일과 우선순위, 테스트 설정 | `application.properties` (main, test) |
| [04](04-docker-compose-postgresql.md) | Docker Compose와 PostgreSQL | `compose.yaml` |
| [05](05-project-structure.md) | 디렉터리 규약과 패키지 설계 | `src/`, `docs/` |
| [06](06-git-workflow.md) | Git 브랜치 전략과 개발 규칙 | `.gitignore`, `.gitattributes`, `CLAUDE.md` |

## 뼈대 전체 그림

```
pawgeun/
├── build.gradle            ← 01  무엇을 가져다 쓰고 어떻게 빌드하는가
├── settings.gradle         ← 01  프로젝트 이름
├── gradlew, gradle/        ← 01  Gradle 실행기 (설치 없이 같은 버전 보장)
├── compose.yaml            ← 04  개발용 PostgreSQL 컨테이너
├── src/main/java/...       ← 02, 05  애플리케이션 코드
├── src/main/resources/     ← 03  설정 파일
├── src/test/...            ← 02, 03  테스트 코드와 테스트 전용 설정
├── docs/                   ← 05  설계, 의사결정 기록, 강의
├── .gitignore, .gitattributes ← 06  Git이 다룰 것과 안 다룰 것
└── CLAUDE.md               ← 06  작업 규칙
```

## 서버가 켜지는 순간에 일어나는 일 (전체 흐름)

```
IntelliJ ▶ 실행 (또는 ./gradlew bootRun)
  → Gradle이 build.gradle대로 의존성을 받아 컴파일 (01)
  → main()이 SpringApplication.run 호출 (02)
  → application.properties를 읽고 (03)
  → spring-boot-docker-compose가 compose.yaml을 보고 PostgreSQL 컨테이너를 띄움 (04)
  → 자동 구성이 classpath를 보고 DataSource, JPA, MVC 등을 만들어 연결 (02)
  → 내장 Tomcat이 8080에서 요청 대기
```
