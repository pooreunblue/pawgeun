# 05. JPA: 엔티티, Repository, 유니크 제약, 트랜잭션

> 이 강의 한 줄 요약: **JPA는 "자바 객체"와 "DB 표" 사이의 통역사다. 우리는 객체에게 말을 걸고, 통역사가 SQL로 바꿔 DB에 전한다.**

## 1. JPA가 해 주는 일

### 비유: 통역사

자바는 객체로 생각하고, DB는 표(행과 열)로 생각한다. 둘은 말이 다르다. 예전에는 개발자가 직접 SQL을 써서 통역했다.

```java
// JPA 없이: 개발자가 직접 통역
// 필수 컬럼(NOT NULL)을 하나라도 빠뜨리면 실행 시 오류가 나므로 전부 직접 챙겨야 한다
String sql = "INSERT INTO member (email, password_hash, nickname, created_at, updated_at) "
        + "VALUES (?, ?, ?, ?, ?)";
LocalDateTime now = LocalDateTime.now();
jdbcTemplate.update(sql, member.getEmail(), member.getPasswordHash(), member.getNickname(), now, now);
```

JPA를 쓰면 객체에게 말하기만 하면 된다.

```java
// JPA: 객체를 저장해 달라고만 말하면 통역사가 INSERT를 만든다
memberRepository.save(member);
```

`Member` 객체를 `save`하면 `INSERT`가, 필드를 바꾸면 `UPDATE`가 나간다. Django ORM의 모델과 같은 자리다.

| Django | Spring Data JPA |
| --- | --- |
| `class Member(models.Model)` | `@Entity class Member` |
| `Member.objects.get(email=...)` | `memberRepository.findByEmail(...)` |
| `unique=True` | `@Column(unique = true)` |
| `auto_now_add`, `auto_now` | `@CreatedDate`, `@LastModifiedDate` |
| migrate | `ddl-auto` (지금) / Flyway (나중) |

## 2. 엔티티 읽기

### 비유: 엔티티는 "주민등록 원본 서류"

엔티티는 DB 표 한 줄을 그대로 옮긴 **원본 서류**다. 원본은 함부로 복사해서 나눠 주지 않고(06강의 DTO가 복사본 역할), 고칠 때도 정해진 절차를 따른다.

```java
@Entity @Table(name = "member")                       // 이 클래스는 member 테이블과 연결된다
@NoArgsConstructor(access = AccessLevel.PROTECTED)    // JPA 전용 기본 생성자 (외부에서 new 금지)
public class Member extends BaseTimeEntity {          // 생성·수정 시각은 부모에게 상속
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)  // PK는 DB가 자동 증가로 만든다
    private Long id;

    @Column(nullable = false, unique = true, length = 100)   // 필수, 중복 불가, 최대 100자
    private String email;
    ...
}
```

- **`@Entity`:** 이 클래스는 테이블과 연결된다.
- **`@Id` + `IDENTITY`:** PK를 DB의 자동 증가(auto increment)가 만든다. 은행 번호표 기계처럼 DB가 1, 2, 3...을 나눠 준다. PostgreSQL에서는 identity 컬럼이 된다.
- **`@NoArgsConstructor(PROTECTED)`:** JPA는 DB에서 읽은 값을 담을 때 **빈 객체부터 만들고** 값을 채운다. 그래서 기본 생성자가 필요하다. 하지만 개발자가 `new Member()`로 이메일도 없는 빈 회원을 만들면 안 되므로 `protected`로 막았다. "직원 전용 출입문"이다. JPA(직원)는 쓰고, 외부 코드(손님)는 못 쓴다.
- **`@Builder`는 private 생성자에 붙였다.** 정해진 필드로만 만들 수 있게 하는 정식 접수 창구다.
- **Setter를 만들지 않았다.** `setEmail()`이 열려 있으면 어디서든 아무 때나 원본 서류를 고쳐 쓸 수 있다. 값은 의미 있는 메서드(예: `changeNickname`)로만 바꾸게 한다. 주민등록 정보를 바꾸려면 "개명 신청"이라는 정해진 절차를 밟는 것처럼. 2단계엔 변경 기능이 없어서 아직 없다.
- **`@Data`를 엔티티에 쓰지 않는다.** `@Data`가 만드는 `equals/hashCode/toString`은 모든 필드를 건드린다. 엔티티끼리 서로를 참조하면(회원 → 반려동물 → 회원 → ...) `toString`이 끝없이 돌거나, 로딩하지 않은 연관 데이터를 불러오느라 의도치 않은 쿼리가 나간다. 거울 두 개를 마주 세운 것처럼 무한히 비친다.

## 3. 공통 시각: BaseTimeEntity

### 비유: 모든 서류에 찍히는 접수인·수정인

모든 서류에는 "언제 접수됐고 언제 마지막으로 고쳤는지" 도장이 찍힌다. 서류 종류마다 이 칸을 새로 그리지 않고, **공통 양식**으로 뺐다.

- **`@MappedSuperclass`:** 테이블은 따로 만들지 않고, 자식 엔티티의 컬럼으로 상속한다. `member` 테이블에 `created_at`, `updated_at` 컬럼이 생긴다.
- **`@EntityListeners(AuditingEntityListener.class)` + `@EnableJpaAuditing`:** 저장·수정 시점에 시각을 자동으로 채운다. 하나는 "도장 찍는 직원"이고 하나는 "도장 찍기 업무 시작" 스위치다. 둘 중 하나라도 빠지면 값이 `null`이 되어 `nullable = false`에서 터진다.

## 4. Repository: 인터페이스만 쓴다

### 비유: 도서관 사서에게 주문서만 건네기

```java
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);   // 이메일로 회원 하나 찾기
    boolean existsByEmail(String email);          // 이메일이 이미 있는지만 확인
}
```

구현 클래스를 쓰지 않았다. **인터페이스(주문서)만** 적으면 Spring Data(사서)가 실행 시점에 구현을 만든다. `JpaRepository`가 `save`, `findById`, `delete`, `count`를 이미 준다.

**메서드 이름이 곧 쿼리다.** 사서는 주문서의 문장을 읽고 무슨 책을 찾을지 안다.

| 메서드 이름 | 만들어지는 쿼리 |
| --- | --- |
| `findByEmail` | `SELECT ... WHERE email = ?` |
| `existsByEmail` | 존재 여부만 조회 (`SELECT 1 ... LIMIT 1` 같은 형태) |
| `findByEmailAndNickname` | `WHERE email = ? AND nickname = ?` |
| `findByNicknameOrderByIdDesc` | `WHERE nickname = ? ORDER BY id DESC` |

이름 규칙(`findBy`, `existsBy`, `And`, `OrderBy` 등)만 맞으면 된다. 오타가 나면 서버 기동 때 바로 실패해서 알려 준다.

**`Optional`을 돌려주면** "없을 수 있음"이 타입에 드러난다. 선물 상자를 받았는데 상자에 "비어 있을 수 있음"이라고 쓰여 있는 것과 같다. 열어 보기 전에 확인해야 한다는 걸 잊지 않는다. `null`을 돌려주면 그런 경고 문구가 없다.

## 5. 유니크는 두 겹으로 지킨다

### 비유: 극장 좌석 예매

```java
// 1겹: 친절한 안내 (먼저 확인하고 정중히 거절)
if (memberRepository.existsByEmail(normalized)) throw new BusinessException(EMAIL_DUPLICATED);
// 2겹: 진짜 방어선 (DB의 UNIQUE 제약)
memberRepository.saveAndFlush(member);
```

극장에서 A1 좌석을 예매한다고 하자.

1. 철수가 "A1 비었어요?" 물어본다 → "네, 비었어요."
2. **같은 순간** 영희도 "A1 비었어요?" 물어본다 → "네, 비었어요."
3. 철수가 예매 버튼을 누른다.
4. 영희도 예매 버튼을 누른다.

둘 다 "비었다"는 답을 들었으니 1번 검사만 믿으면 **중복 예매**가 된다. 확인과 저장 사이에 틈이 있기 때문이다(경쟁 조건, race condition).

최후의 방어선은 **좌석 자체**다. 이미 누가 앉은 의자에는 물리적으로 또 앉을 수 없다. DB의 **`UNIQUE` 제약**이 그 의자다. 두 번째 저장은 `DataIntegrityViolationException`으로 실패한다.

원칙: **중복 방지는 애플리케이션 검사만 믿지 말고 DB 제약으로 반드시 보장한다.** 앞의 `exists` 검사는 대부분의 경우에 친절한 메시지를 주기 위한 것이고, 진짜 보장은 DB가 한다.

### 왜 save가 아니라 saveAndFlush인가

일반적으로 JPA는 SQL을 바로 보내지 않고 모아 두었다가 **flush 시점(보통 트랜잭션 커밋 직전)**에 보낸다. 메모에 적어 두고 나중에 한꺼번에 처리하는 것이다. 그러면 예외가 `signUp` 메서드가 다 끝난 뒤(커밋 시점)에야 터져서, 메서드 안의 `try`로 잡을 수 없다.

그런데 `Member`는 `GenerationType.IDENTITY`를 쓴다. IDENTITY는 **DB가 INSERT를 해야만 id를 알려 준다**(번호표 기계에 실제로 들어가야 번호가 나온다). JPA는 영속화(`persist`)할 때 id가 필요하므로, 새 엔티티라면 `save`만 호출해도 **그 자리에서 INSERT가 실행된다.** 즉 이 프로젝트에서는 `save`만으로도 유니크 제약 위반이 메서드 안에서 터진다.

| 메서드 | 하는 일 | IDENTITY 엔티티 신규 저장 시 |
| --- | --- | --- |
| `save` | 영속화(`persist`)만 요청 | id를 얻으려고 INSERT가 즉시 실행된다 |
| `saveAndFlush` | 영속화 + **flush**: 지금까지 쌓인 변경 사항을 전부 DB로 보낸다 | INSERT는 물론, 같은 트랜잭션에 남아 있던 다른 변경(UPDATE 등)까지 강제로 보낸다 |

그래도 `saveAndFlush`를 쓰는 이유: "DB 반영이 여기서 끝나야 한다"는 의도를 코드에 **명시**하기 위해서다. 나중에 id 전략을 SEQUENCE 등으로 바꾸면 `save`는 다시 INSERT를 미루게 되어 `try`가 예외를 놓친다. `saveAndFlush`는 id 전략과 상관없이 예외가 그 자리에서 나고, `try`로 잡아 `EMAIL_DUPLICATED`(409)로 바꿀 수 있다.

### 이메일 정규화

이메일은 저장 전에 `trim().toLowerCase(Locale.ROOT)`로 정규화한다. `A@x.com`과 `a@x.com`, `" a@x.com "`이 서로 다른 계정이 되는 일을 막는다. 극장이 "A1"과 "a1"을 다른 좌석으로 팔면 곤란한 것과 같다.

`Locale.ROOT`는 JVM 언어 설정에 결과가 흔들리지 않게 한다. 예를 들어 터키어 설정에서는 대문자 `I`를 소문자로 바꾸면 `i`가 아니라 점 없는 `ı`가 된다. 서버 설정에 따라 같은 이메일이 다르게 저장되는 사고를 막는다.

## 6. 트랜잭션

### 비유: 계좌 이체

트랜잭션은 "전부 성공하거나 전부 취소"되는 작업 묶음이다. 계좌 이체가 대표적이다.

```text
1. 철수 계좌에서 10만 원 빼기
2. 영희 계좌에 10만 원 넣기
```

1번만 되고 2번에서 서버가 죽으면 10만 원이 증발한다. 트랜잭션은 "둘 다 되거나, 둘 다 없던 일로" 만든다. 중간에 실패하면 시간을 되감아(롤백) 1번도 취소한다.

```java
@Service
@Transactional(readOnly = true)      // 클래스 기본: 읽기 전용
public class MemberService {
    @Transactional                   // 쓰기 메서드만 덮어씀
    public Member signUp(...)
```

- **`readOnly = true`:** 변경 감지(더티 체킹) 비용을 줄이는 **힌트**다. "이 손님은 구경만 할 거예요"라고 직원에게 미리 알려 주는 것이다. 직원은 그 손님의 장바구니를 덜 신경 쓴다. 하지만 **쓰기를 막는 자물쇠는 아니다.** Hibernate가 반영을 건너뛸 뿐이고, DB·드라이버에 따라 거부 여부가 다르다. 이것에 기대면 안 된다. 조회가 대부분이니 기본값으로 두고, 쓰는 메서드만 `@Transactional`로 덮는다.
- **커밋과 롤백:** 메서드가 예외 없이 끝나면 커밋, `RuntimeException`이 나면 롤백한다. 그래서 `BusinessException`은 `RuntimeException`이다. 중복 가입 같은 업무 오류가 나면 그 메서드에서 한 일이 전부 취소된다.
- **서비스 계층에 둔다.** 컨트롤러는 "주문 접수"를 하고, 서비스가 "하나의 업무 단위"를 처리한다. 이체라는 업무의 시작과 끝을 아는 곳은 서비스다.
- **영속성 컨텍스트:** 트랜잭션 안에서 조회한 엔티티는 1차 캐시(작업대)에 올라 있다. 작업대 위의 서류를 고치면, 커밋 시점에 JPA가 "처음 꺼냈을 때와 달라졌네"를 알아채고 자동으로 `UPDATE`를 보낸다(더티 체킹). `save`를 다시 부르지 않아도 된다. 3단계부터 이 기능을 쓴다.

`application.properties`의 `spring.jpa.open-in-view=false`는 트랜잭션이 끝난 뒤 컨트롤러·뷰 단계에서 지연 로딩이 DB를 건드리는 것을 막는 설정이다. 업무가 끝났는데도 DB 연결을 붙잡고 있지 않게 한다. API 서버에서는 꺼 두는 것이 안전하다.

## 7. ddl-auto는 임시다

`ddl-auto=update`는 엔티티를 보고 테이블을 자동으로 맞춘다. 비유하면 **임시 가건물**이다. 빨리 세우기엔 편하지만, 벽을 허물거나 방 이름을 바꾸는 일(컬럼 삭제·이름 변경)은 못 하고, 사람이 살기 시작한 건물(운영 DB)에서 함부로 쓰면 위험하다.

데이터가 쌓이기 시작하는 시점(배포 전)에 Flyway로 바꾼다. Flyway는 "1번 공사: 테이블 생성, 2번 공사: 컬럼 추가"처럼 **공사 기록(마이그레이션 파일)**을 남기며 순서대로 고친다.

## 직접 해 보기

1. `spring.jpa.show-sql=true`를 켜고 가입·로그인을 호출해 어떤 SQL이 나가는지 보자. `existsByEmail`은 어떤 쿼리인가?
2. `MemberService.signUp`의 `@Transactional`을 지우고 실행해 보자. 가입 후 DB에 실제로 저장되는지 확인해 보자. 저장되든 안 되든, `readOnly`를 쓰기 방지 수단으로 믿으면 안 되는 이유를 설명해 보자.
3. `existsByEmail` 검사를 주석 처리하고 같은 이메일로 두 번 가입해 보자. 어떤 예외와 상태 코드가 나오는가? 이것이 DB 유니크 제약의 역할이다.
4. 생각해 보기: `Member`에 `@Setter`를 달면 안 되는 이유를, `passwordHash`를 예로 들어 설명해 보자.
