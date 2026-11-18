# 01. Gradle: 빌드 도구, 의존성, wrapper

## 1. 빌드 도구가 하는 일

자바 프로젝트는 코드를 쓰는 것만으로는 실행되지 않는다. 아래 일을 누군가 해 줘야 한다.

1. 필요한 라이브러리(Spring, PostgreSQL 드라이버 등)를 내려받는다.
2. 소스를 컴파일한다.
3. 테스트를 실행한다.
4. 실행 가능한 파일(jar)로 묶는다.

이 일을 자동화하는 것이 빌드 도구다. Gradle이 그것이고, 규칙을 `build.gradle`에 적는다.

| Python/Django | 자바/Gradle |
| --- | --- |
| `requirements.txt`, `pip install` | `build.gradle`의 `dependencies`, 자동 다운로드 |
| `venv` | 프로젝트별 의존성은 Gradle 캐시(`~/.gradle`)에서 관리 |
| `python manage.py runserver` | `./gradlew bootRun` |
| `python manage.py test` | `./gradlew test` |
| 배포용 묶음 없음(소스 그대로) | `./gradlew bootJar`로 jar 하나 생성 |

자바에는 Maven도 있다. 같은 역할이고 설정을 XML(`pom.xml`)로 쓴다. Gradle은 스크립트 형태라 더 짧고 유연하다. 두 도구의 개념(의존성, 저장소, 생명주기)은 거의 같아서 하나를 알면 다른 하나도 읽힌다.

## 2. build.gradle 한 줄씩

### plugins

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.2'
    id 'io.spring.dependency-management' version '1.1.7'
}
```

플러그인은 Gradle에 "할 줄 아는 일"을 추가한다.

| 플러그인 | 추가되는 것 |
| --- | --- |
| `java` | `compileJava`, `test`, `jar` 같은 기본 작업과 소스 디렉터리 규약 |
| `org.springframework.boot` | `bootRun`(실행), `bootJar`(실행 가능한 jar), `developmentOnly` 설정 |
| `io.spring.dependency-management` | Spring Boot가 검증한 **버전 목록(BOM)**을 적용해, 의존성에 버전을 안 써도 되게 함 |

`4.1.2`가 Spring Boot 버전이고, 이 숫자 하나가 Spring, Hibernate, Jackson, Tomcat 등 수십 개 라이브러리의 호환되는 버전을 결정한다. 그래서 `dependencies`에 `spring-boot-starter-data-jpa`처럼 버전이 없다. 이것이 의존성 관리의 핵심 이점이다.

### group, version, java toolchain

```groovy
group = 'org.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}
```

- `group`: 라이브러리를 배포할 때 쓰는 식별자의 일부. 실제 패키지는 `.java` 파일의 `package` 선언이 정하며, 관례상 group과 맞춰 `org.example.pawgeun`으로 쓴다.
- `SNAPSHOT`: "개발 중인 버전"이라는 관례적 표시.
- `toolchain`: **어떤 JDK로 컴파일할지**를 프로젝트가 정한다. 컴퓨터에 JDK 26이 기본으로 깔려 있어도 Gradle은 21을 찾아 쓴다. 팀원마다 JDK가 달라도 같은 결과를 내려는 장치다. 이 컴퓨터에는 JDK 17, 21, 26이 함께 있어서 특히 중요하다.

### repositories

```groovy
repositories { mavenCentral() }
```

라이브러리를 어디서 내려받을지다. Maven Central은 자바 라이브러리의 거대한 공용 저장소로, PyPI와 같은 자리다.

### dependencies와 설정(configuration)

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    ...
}
```

`그룹:이름` 형식(`org.springframework.boot` : `spring-boot-starter-data-jpa`)이다. 앞의 단어(`implementation`)는 **이 의존성을 언제 쓰는지**를 뜻한다. 이 프로젝트에서 쓰는 것을 모두 정리한다.

| 설정 | 컴파일 때 | 실행 때 | 테스트 때 | 이 프로젝트의 예 |
| --- | --- | --- | --- | --- |
| `implementation` | O | O | O | JPA, Validation, WebMVC (코드에서 직접 씀) |
| `compileOnly` | O | X | X | Lombok (컴파일할 때 코드를 만들어 주고 끝) |
| `annotationProcessor` | 컴파일 중 실행 | X | X | Lombok (실제로 코드를 생성하는 부분) |
| `runtimeOnly` | X | O | O | PostgreSQL 드라이버 (코드에서 import 안 함, 실행 때만 필요) |
| `developmentOnly` | X | 개발 실행만 | X | docker-compose 지원 (배포 jar에는 안 들어감) |
| `testImplementation` | 테스트 컴파일 O | X | O | `*-test` 스타터 (JUnit, AssertJ, MockMvc 등) |
| `testRuntimeOnly` | X | X | O | H2, JUnit 플랫폼 런처 |

**왜 이렇게 나누나:** 필요한 곳에만 넣어 배포 크기를 줄이고, 의도하지 않은 사용을 막기 위해서다. 예를 들어 PostgreSQL 드라이버를 `implementation`이 아니라 `runtimeOnly`로 둔 것은 "코드는 `javax.sql.DataSource` 같은 표준 인터페이스만 쓰고, 구체 드라이버는 실행 때 갈아 끼운다"는 설계를 코드로 강제한다. 실수로 `org.postgresql.*` 클래스를 import하면 컴파일 오류가 난다.

Lombok이 둘로 나뉜 이유: `compileOnly`는 `@Getter` 같은 어노테이션 클래스를 컴파일러에게 알려 주고, `annotationProcessor`는 컴파일 도중 실제 코드를 생성하는 프로그램을 돌린다. 둘 다 있어야 한다. 테스트 코드에서도 Lombok을 쓰려면 `testCompileOnly`와 `testAnnotationProcessor`를 따로 선언해야 한다(테스트 소스는 별도 컴파일 단위다).

### 스타터(starter)

`spring-boot-starter-*`는 **관련 의존성 묶음**이다.

| 스타터 | 포함하는 것 | 이 프로젝트에서의 용도 |
| --- | --- | --- |
| `starter-webmvc` | Spring MVC, 내장 Tomcat, Jackson | REST API |
| `starter-data-jpa` | Hibernate, Spring Data JPA, 트랜잭션 | DB 접근 |
| `starter-validation` | Bean Validation(Hibernate Validator) | 요청 검증 |

참고: 이전 버전 자료에서 보던 `spring-boot-starter-web`이 Boot 4에서는 `starter-webmvc`로 이름이 나뉘었다. 인터넷의 옛 예제를 따라 하다 이름이 안 맞으면 버전 차이를 의심한다. 테스트 스타터(`starter-data-jpa-test`, `starter-validation-test`, `starter-webmvc-test`)도 기술별로 따로 있어서, 쓰는 기능에 맞는 것을 추가한다.

### test 작업

```groovy
tasks.named('test') { useJUnitPlatform() }
```

테스트를 JUnit 5 플랫폼으로 실행하라는 뜻이다. 이 줄이 없으면 테스트가 실행되지 않는다.

## 3. settings.gradle

```groovy
rootProject.name = 'pawgeun'
```

프로젝트 이름이다. jar 파일 이름(`pawgeun-0.0.1-SNAPSHOT.jar`)과 IntelliJ 프로젝트 표시에 쓰인다. 하위 모듈이 여러 개인 멀티 모듈 프로젝트에서는 여기에 `include`를 쓴다. 지금은 단일 모듈이다.

## 4. Gradle Wrapper: gradlew

프로젝트 루트의 `gradlew`(맥/리눅스)와 `gradlew.bat`(윈도우)는 **Gradle 자신을 내려받아 실행해 주는 스크립트**다.

```
gradle/wrapper/gradle-wrapper.properties  →  distributionUrl=...gradle-9.8.2-bin.zip
gradle/wrapper/gradle-wrapper.jar         →  위 주소에서 내려받는 작은 프로그램
```

처음 `./gradlew build`를 하면 9.8.2 버전을 `~/.gradle/wrapper/dists`에 받아 두고 이후엔 재사용한다.

**왜 쓰나:** 컴퓨터마다 Gradle을 따로 설치하면 버전이 달라 "내 컴퓨터에선 되는데"가 생긴다. wrapper는 모두가 같은 버전을 쓰게 한다. 그래서 `gradlew`와 `gradle-wrapper.jar`는 **Git에 커밋한다**(`.gitignore`가 jar를 예외로 풀어 두었고, `.gitattributes`가 jar를 바이너리로 표시한다). 반대로 Gradle 자체를 설치할 필요는 없다.

`.gitattributes`의 `/gradlew text eol=lf`는 줄바꿈을 항상 LF로 맞춘다. 윈도우에서 CRLF로 바뀌면 맥/리눅스에서 스크립트가 깨지기 때문이다.

## 5. 자주 쓰는 명령

```bash
./gradlew bootRun            # 서버 실행
./gradlew test               # 테스트
./gradlew build              # 컴파일 + 테스트 + jar
./gradlew bootJar            # 실행 가능한 jar만 (build/libs/)
./gradlew clean              # build/ 삭제
./gradlew dependencies       # 의존성 트리 출력 (충돌·누락 추적)
./gradlew test --tests '*JwtProviderTest'   # 특정 테스트만
./gradlew build --refresh-dependencies      # 캐시 무시하고 다시 내려받기
```

`./gradlew`처럼 앞에 `./`를 붙이는 것은 현재 폴더의 스크립트를 실행한다는 뜻이다.

### 빌드가 빠른 이유

Gradle은 작업마다 입력과 출력을 기록해서 바뀌지 않았으면 건너뛴다. 출력에 `UP-TO-DATE`가 보이면 그 작업을 생략했다는 뜻이다. `build/`와 `.gradle/` 폴더가 이 기록과 결과물을 담으며, 둘 다 `.gitignore` 대상이다.

## 6. IntelliJ와의 관계

IntelliJ는 `build.gradle`을 읽어 프로젝트를 구성한다. 의존성을 추가한 뒤에는 코끼리 아이콘(Reload All Gradle Projects)을 눌러 다시 읽힌다. 두 설정을 확인한다.

- Settings > Build, Execution, Deployment > Build Tools > Gradle > **Gradle JVM**: 21
- Project Structure > **SDK**: 21

JDK가 맞지 않으면 "unsupported class file version" 같은 오류가 난다. 이 컴퓨터는 기본 `java`가 26이라 특히 맞춰 주어야 한다. 터미널에서는 `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`처럼 지정할 수 있다.

## 7. 흔한 오류

| 증상 | 원인과 해결 |
| --- | --- |
| `Could not resolve ...` | 인터넷 연결, 의존성 이름 오타, 버전 없는 라이브러리(BOM에 없는 것은 버전을 직접 써야 함) |
| `package ... does not exist` | 의존성을 안 넣었거나, Gradle 새로고침을 안 함 |
| Lombok 어노테이션이 안 먹음 | `annotationProcessor` 누락, IntelliJ의 Enable annotation processing 꺼짐 |
| 테스트가 0개 실행됨 | `useJUnitPlatform()` 누락 |
| `Unsupported class file major version` | JDK/Gradle 버전 불일치 |

## 직접 해 보기

1. `./gradlew dependencies --configuration runtimeClasspath`를 실행해 보자. PostgreSQL 드라이버가 보이는가? `--configuration compileClasspath`에서는 어떤가?
2. `build.gradle`에서 `runtimeOnly 'org.postgresql:postgresql'`을 `implementation`으로 바꿔도 동작은 같다. 그런데도 왜 `runtimeOnly`가 더 나은지 한 문장으로 설명해 보자. (확인 후 원복)
3. `./gradlew bootJar` 후 `build/libs/`의 jar를 `java -jar`로 실행해 보자. 서버가 뜨지 않고 DataSource 관련 오류가 날 것이다. 왜일까? (힌트: `developmentOnly`로 선언한 docker-compose 지원은 jar에 들어가지 않는다. 그러면 DB 접속 정보는 누가 주는가? 03강의 환경변수로 줄 수 있다. 단 PostgreSQL 컨테이너가 꺼져 있으면 접속할 수 없으니 먼저 `docker compose up -d`로 DB를 켠다. 그다음 `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:54321/pawgeun`, `SPRING_DATASOURCE_USERNAME=pawgeun`, `SPRING_DATASOURCE_PASSWORD=pawgeun`을 주고 `java -jar`를 다시 실행해 보자. 이 `pawgeun` 값은 PR #1의 이름 변경 이후의 `compose.yaml` 기준이다.)
4. `./gradlew build`를 두 번 연속 실행하고 두 번째 출력에서 `UP-TO-DATE`를 찾아 보자.
