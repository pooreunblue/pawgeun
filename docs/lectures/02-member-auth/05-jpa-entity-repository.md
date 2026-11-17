# 05. JPA: 엔티티, Repository, 유니크 제약, 트랜잭션

## 1. JPA가 해 주는 일

SQL 대신 자바 객체로 DB를 다룬다. `Member` 객체를 `save`하면 `INSERT`가, 필드를 바꾸면 `UPDATE`가 나간다. Django ORM의 모델과 같은 자리다.

| Django | Spring Data JPA |
| --- | --- |
| `class Member(models.Model)` | `@Entity class Member` |
| `Member.objects.get(email=...)` | `memberRepository.findByEmail(...)` |
| `unique=True` | `@Column(unique = true)` |
| `auto_now_add`, `auto_now` | `@CreatedDate`, `@LastModifiedDate` |
| migrate | `ddl-auto` (지금) / Flyway (나중) |

## 2. 엔티티 읽기

```java
@Entity @Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;
    ...
}
```

- `@Entity`: 이 클래스는 테이블과 연결된다.
- `@Id` + `IDENTITY`: PK를 DB의 auto increment가 만든다. PostgreSQL에서는 identity 컬럼이 된다.
- `@NoArgsConstructor(PROTECTED)`: JPA는 객체를 만들 때 **기본 생성자**가 필요하다. 하지만 개발자가 `new Member()`로 빈 객체를 만들면 안 되므로 `protected`로 막았다.
- `@Builder`는 private 생성자에 붙여, 정해진 필드로만 만들 수 있게 했다.
- **Setter를 만들지 않았다.** 값은 의미 있는 메서드(예: `changeNickname`)로만 바꾸게 하는 것이 원칙이다. 2단계엔 변경 기능이 없어서 아직 없다.
- `@Data`를 엔티티에 쓰지 않는다. `equals/hashCode/toString`이 연관관계와 엮여 무한 순환이나 의도치 않은 지연 로딩을 일으킨다.

## 3. 공통 시각: BaseTimeEntity

`createdAt`, `updatedAt`을 모든 엔티티에 반복해 쓰지 않으려고 부모 클래스로 뺐다.

- `@MappedSuperclass`: 테이블은 안 만들고, 자식 엔티티의 컬럼으로 상속한다.
- `@EntityListeners(AuditingEntityListener.class)` + `@EnableJpaAuditing`: 저장·수정 시점에 시각을 자동으로 채운다. 이 두 가지 중 하나라도 빠지면 값이 `null`이 되어 `nullable = false`에서 터진다.

## 4. Repository: 인터페이스만 쓴다

```java
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

구현 클래스를 쓰지 않았다. Spring Data가 실행 시점에 만든다. `JpaRepository`가 `save`, `findById`, `delete`, `count`를 이미 준다.

**메서드 이름이 곧 쿼리다.** `findByEmail` → `SELECT ... WHERE email = ?`, `existsByEmail` → 존재 여부만 조회. 이름 규칙(`findBy`, `existsBy`, `And`, `OrderBy` 등)만 맞으면 된다. `Optional`을 돌려주면 "없을 수 있음"이 타입에 드러나서 `null` 검사를 잊지 않는다.

## 5. 유니크는 두 겹으로 지킨다

```java
if (memberRepository.existsByEmail(normalized)) throw new BusinessException(EMAIL_DUPLICATED);
memberRepository.save(member);
```

위 `exists` 검사는 친절한 오류 메시지를 위한 것이다. 하지만 두 요청이 **동시에** 같은 이메일로 들어오면 둘 다 검사를 통과한다(검사와 저장 사이에 틈이 있다). 그때 최후의 방어선이 DB의 **`UNIQUE` 제약**이다. 하나는 `DataIntegrityViolationException`으로 실패한다.

원칙: 중복 방지는 애플리케이션 검사만 믿지 말고 DB 제약으로 반드시 보장한다. 지금은 이 동시성 실패를 별도로 변환하지 않아 500이 된다. 현실적으로 드문 경우라 두었고, 필요하면 `GlobalExceptionHandler`에 `DataIntegrityViolationException` → `EMAIL_DUPLICATED` 변환을 추가한다.

이메일은 저장 전에 `trim().toLowerCase()`로 정규화한다. `A@x.com`과 `a@x.com`이 서로 다른 계정이 되는 일을 막는다.

## 6. 트랜잭션

트랜잭션은 "전부 성공하거나 전부 취소"되는 작업 묶음이다.

```java
@Service
@Transactional(readOnly = true)      // 클래스 기본: 읽기 전용
public class MemberService {
    @Transactional                   // 쓰기 메서드만 덮어씀
    public Member signUp(...)
```

- `readOnly = true`: 변경 감지(더티 체킹) 비용을 줄이고, 실수로 쓰는 것을 막는다. 조회가 대부분이니 기본값으로 두고, 쓰는 메서드만 `@Transactional`로 덮는다.
- 메서드가 예외 없이 끝나면 커밋, `RuntimeException`이 나면 롤백한다. 그래서 `BusinessException`은 `RuntimeException`이다.
- **서비스 계층에 둔다.** 컨트롤러가 아니라 서비스가 "하나의 업무 단위"이기 때문이다.
- **영속성 컨텍스트:** 트랜잭션 안에서 조회한 엔티티는 1차 캐시에 올라 있다. 값을 바꾸면 커밋 시점에 자동으로 `UPDATE`가 나간다(더티 체킹). 3단계부터 이 기능을 쓴다.

`application.properties`의 `spring.jpa.open-in-view=false`는 트랜잭션이 끝난 뒤 컨트롤러·뷰 단계에서 지연 로딩이 DB를 건드리는 것을 막는 설정이다. API 서버에서는 꺼 두는 것이 안전하다.

## 7. ddl-auto는 임시다

`ddl-auto=update`는 엔티티를 보고 테이블을 자동으로 맞춘다. 개발엔 편하지만 컬럼 삭제·이름 변경은 못 하고 운영에서 위험하다. 데이터가 쌓이기 시작하는 시점(배포 전)에 Flyway로 바꾼다.

## 직접 해 보기

1. `spring.jpa.show-sql=true`를 켜고 가입·로그인을 호출해 어떤 SQL이 나가는지 보자. `existsByEmail`은 어떤 쿼리인가?
2. `MemberService.signUp`의 `@Transactional`을 지우고 실행해 보자. `readOnly = true`인 클래스 설정이 적용돼 어떤 문제가 생기는가? (힌트: 읽기 전용 트랜잭션에서의 쓰기)
3. `existsByEmail` 검사를 주석 처리하고 같은 이메일로 두 번 가입해 보자. 어떤 예외와 상태 코드가 나오는가? 이것이 DB 유니크 제약의 역할이다.
4. 생각해 보기: `Member`에 `@Setter`를 달면 안 되는 이유를, `passwordHash`를 예로 들어 설명해 보자.
