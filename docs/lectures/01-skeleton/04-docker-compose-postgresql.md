# 04. Docker Compose와 PostgreSQL

## 1. 왜 DB를 컨테이너로 띄우나

개발하려면 PostgreSQL이 필요하다. 컴퓨터에 직접 설치하면 이런 문제가 생긴다.

- 버전이 컴퓨터마다 다르다.
- 다른 프로젝트의 DB와 섞이고 포트가 충돌한다.
- 지우고 처음부터 다시 해 보기가 번거롭다.
- 새 팀원이 설치·설정 문서를 따라 해야 한다.

Docker 컨테이너로 띄우면 `compose.yaml` 한 파일이 "PostgreSQL 17을 이 설정으로"를 정의하고, 누가 실행해도 똑같다.

## 2. Docker 개념 네 가지

| 개념 | 설명 | 비유 |
| --- | --- | --- |
| 이미지(image) | 실행에 필요한 모든 것을 담은 읽기 전용 패키지 (`postgres:17`) | 설치 파일, 클래스 |
| 컨테이너(container) | 이미지를 실제로 실행한 격리된 프로세스 | 실행 중인 인스턴스, 객체 |
| 볼륨(volume) | 컨테이너가 지워져도 남는 데이터 저장 공간 | 외장 하드 |
| 포트 매핑 | 컨테이너 안의 포트를 내 컴퓨터의 포트에 연결 | 문 연결 |

**컨테이너는 쓰고 버리는 것**이다. 지우면 안의 데이터도 사라진다. DB 파일을 볼륨에 두는 이유다.

Docker Compose는 여러 컨테이너(여기선 하나)를 YAML 파일로 정의해 한 번에 관리하는 도구다.

## 3. compose.yaml 한 줄씩

```yaml
services:
  postgres:                              # 서비스 이름
    image: 'postgres:17'                 # 공식 PostgreSQL 17 이미지
    environment:
      - 'POSTGRES_DB=pawgeun'            # 처음 만들 DB 이름
      - 'POSTGRES_USER=pawgeun'          # 사용자
      - 'POSTGRES_PASSWORD=pawgeun'      # 비밀번호
    ports:
      - '54321:5432'                     # 내 컴퓨터 54321 → 컨테이너 5432
    volumes:
      - 'pawgeun-db:/var/lib/postgresql/data'   # DB 파일을 볼륨에 저장

volumes:
  pawgeun-db:                            # 볼륨 선언
```

### 이미지 태그

`postgres:17`은 17 버전 계열을 쓴다. 처음 뼈대는 `postgres:latest`였는데 고정 버전으로 바꿨다. `latest`는 시점마다 가리키는 버전이 달라 어느 날 메이저 버전이 올라가 기존 데이터 파일과 호환이 깨질 수 있다. **재현 가능성을 위해 버전을 고정한다.**

### 포트: 왜 54321인가

`'호스트:컨테이너'` 순서다. PostgreSQL의 기본 포트는 5432인데, 내 컴퓨터에 이미 PostgreSQL이 있거나 다른 프로젝트 컨테이너가 5432를 쓰고 있을 수 있어서 호스트 쪽만 54321로 바꿨다. 컨테이너 안에서는 여전히 5432이고, 외부에서 접속할 때만 54321을 쓴다.

### 환경변수는 "처음 한 번"만 적용된다

`POSTGRES_DB/USER/PASSWORD`는 **데이터 디렉터리가 비어 있을 때(최초 생성 시)** 만 쓰인다. 볼륨에 이미 DB가 있으면 값을 바꿔도 반영되지 않는다. "비밀번호를 바꿨는데 접속이 안 돼요"의 단골 원인이다. 초기화하려면 볼륨을 지운다(아래).

## 4. Spring Boot가 compose를 자동으로 다루는 방법

```groovy
developmentOnly 'org.springframework.boot:spring-boot-docker-compose'
```

이 의존성 하나로, 앱을 시작할 때(IntelliJ 실행, `bootRun`) 아래가 자동으로 일어난다.

```
1. 프로젝트 루트에서 compose.yaml을 찾는다
2. docker compose up 으로 컨테이너를 띄운다 (이미 떠 있으면 재사용)
3. 컨테이너의 이미지가 postgres인 것을 알아보고, 포트와 계정(DB, USER, PASSWORD)을 읽는다
4. spring.datasource.url / username / password 를 그 값으로 자동 설정한다
5. 앱을 종료하면 컨테이너도 stop 한다 (삭제는 아님, 데이터는 보존)
```

그래서 `application.properties`에 DB 주소가 한 줄도 없다. 값은 compose 파일 한 곳에서만 관리된다. "단 하나의 진실 공급원"이다.

- `developmentOnly`이므로 배포용 jar에는 포함되지 않는다. 운영에서는 환경변수로 DB 주소를 준다(03강).
- 테스트에서는 기본적으로 건너뛴다. 우리는 H2를 쓰므로 `spring.docker.compose.enabled=false`로 명시했다(03강).
- 컨테이너 포트 5432가 호스트의 어느 포트로 매핑됐는지도 Spring이 알아서 읽으므로, 호스트 포트를 바꿔도 앱 설정은 고치지 않는다.

## 5. 접속과 확인

```bash
docker compose up -d          # 직접 띄우기 (백그라운드)
docker compose ps             # 상태
docker compose logs -f postgres   # 로그
docker compose stop           # 정지 (데이터 보존)
docker compose down           # 컨테이너 삭제 (볼륨은 보존)
docker compose down -v        # 컨테이너와 볼륨까지 삭제 (데이터 전부 삭제!)
```

DB 안을 들여다보려면:

```bash
docker compose exec postgres psql -U pawgeun -d pawgeun
# psql 안에서
\dt                  # 테이블 목록
\d member            # member 테이블 구조 (2단계 이후)
SELECT * FROM member;
\q                   # 종료
```

IntelliJ Ultimate의 Database 도구나 DBeaver로 접속할 때는 `localhost`, 포트 `54321`, DB·사용자·비밀번호 `pawgeun`를 쓴다.

## 6. PostgreSQL과 H2의 차이

| | PostgreSQL (개발·운영) | H2 (테스트) |
| --- | --- | --- |
| 형태 | 별도 서버 프로세스 | 자바 프로세스 안의 라이브러리 |
| 데이터 | 디스크(볼륨)에 보존 | 메모리, 종료하면 사라짐 |
| 속도 | 네트워크 왕복 있음 | 매우 빠름 |
| 기능 | 풍부함(JSON, 전문 검색, 확장) | 일부만 흉내 냄 (`MODE=PostgreSQL`) |

테스트가 H2로 통과해도 PostgreSQL 고유 기능에서는 다르게 동작할 수 있다. 그 기능을 쓰기 시작하면 Testcontainers(테스트가 진짜 PostgreSQL 컨테이너를 띄움)로 옮긴다.

## 7. 커넥션 풀

DB 연결은 만드는 데 비용이 든다. 요청마다 새로 연결하면 느리므로, 연결을 미리 몇 개 만들어 두고 빌려 쓰고 돌려주는 **커넥션 풀**을 쓴다. Spring Boot의 기본 구현이 HikariCP이고 기동 로그의 `HikariPool-1 - Start completed`가 그것이다. 풀 크기 기본값은 10이다. 지금은 건드릴 일이 없지만, "요청이 느려지고 `Connection is not available` 오류"가 나면 커넥션을 오래 잡고 있는 코드(긴 트랜잭션, 외부 API 호출을 트랜잭션 안에서 하는 경우)를 의심한다. 하루 한 번 AI 이미지를 생성하는 이후 단계에서 중요해지는 주제다.

## 8. 흔한 문제

| 증상 | 원인과 해결 |
| --- | --- |
| `Cannot connect to the Docker daemon` | Docker Desktop이 꺼져 있음. 켜고 다시 실행 |
| `port is already allocated` | 54321을 다른 것이 쓰는 중. 호스트 포트를 바꾸거나 충돌 프로세스 종료 |
| 앱은 뜨는데 `password authentication failed` | 볼륨에 예전 비밀번호로 만든 DB가 남음. `docker compose down -v` 후 재시작 (데이터 삭제 주의) |
| 데이터가 갑자기 사라짐 | `down -v`를 실행했거나 볼륨 이름을 바꿈 |
| 이미지 내려받기가 느림 | 처음 한 번만. 이후엔 캐시 |

## 직접 해 보기

1. Docker를 켠 채 서버를 실행하고 `docker compose ps`로 컨테이너가 떴는지 확인하자. 서버를 종료한 뒤 다시 확인하면 상태가 어떻게 바뀌는가?
2. `psql`로 접속해 `\dt`를 실행해 보자. 2단계 코드로 서버를 한 번 띄운 뒤라면 `member` 테이블이 보인다. `\d member`로 `email`의 유니크 제약을 찾아 보자.
3. 가입 API로 회원을 만든 뒤 `docker compose stop` → `docker compose up -d` 하고 `SELECT * FROM member;`를 해 보자. 데이터가 남아 있는가? 이어서 `docker compose down`은? `down -v`는?
4. Docker를 끄고 서버를 실행하면 어떤 오류가 나는가? 02강의 오류 표와 비교해 보자.
5. 생각해 보기: DB 비밀번호가 `pawgeun`로 단순한 것이 지금은 괜찮은 이유와 운영에서는 안 되는 이유를 설명해 보자.
