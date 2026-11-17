# 06. DTO, record, Bean Validation

## 1. DTO란

DTO(Data Transfer Object)는 **요청과 응답의 모양**만 정의한 객체다. 엔티티와 따로 만든다.

```
요청 JSON ──▶ SignUpRequest (DTO) ──▶ 서비스 ──▶ Member (엔티티) ──▶ DB
응답 JSON ◀── MemberResponse (DTO) ◀── 서비스 ◀── Member (엔티티)
```

### 엔티티를 그대로 내보내면 안 되는 이유

1. **정보 누출.** `Member`를 그대로 직렬화하면 `passwordHash`가 응답에 실린다. `MemberResponse`는 `id, email, nickname`만 가져서 이 사고가 구조적으로 불가능하다.
2. **API와 DB가 묶인다.** 컬럼 이름을 바꾸면 앱이 깨진다. DTO를 두면 DB를 바꿔도 API는 그대로 둘 수 있다.
3. **요청에서도 위험하다.** 요청을 엔티티로 받으면 클라이언트가 `id`나 권한 같은 필드를 임의로 채워 보낼 수 있다(과다 바인딩, mass assignment).

## 2. record: DTO에 딱 맞는 문법

```java
public record SignUpRequest(String email, String password, String nickname) { }
```

`record`(자바 16+)는 불변 데이터 묶음이다. 필드, 생성자, getter(`email()`), `equals/hashCode/toString`을 자동 생성한다. Lombok 없이 한 줄이다. Jackson이 JSON을 record로 바로 변환한다.

Django의 serializer/form과 비슷한 자리지만, 모양 정의와 검증 규칙이 어노테이션으로 한곳에 모인다.

## 3. Bean Validation: 어노테이션으로 검증

```java
public record SignUpRequest(
    @NotBlank @Email @Size(max = 100) String email,
    @NotBlank @Pattern(regexp = "...", message = "...") String password,
    @NotBlank @Size(min = 2, max = 30) String nickname) { }
```

컨트롤러에서 `@Valid`를 붙이면 검증이 실행된다.

```java
public MemberResponse signUp(@Valid @RequestBody SignUpRequest request)
```

검증에 실패하면 컨트롤러 메서드가 **실행되기 전에** `MethodArgumentNotValidException`이 던져지고, `GlobalExceptionHandler`가 400 JSON으로 바꾼다(07강).

| 어노테이션 | 검사 | 주의 |
| --- | --- | --- |
| `@NotNull` | null 아님 | 빈 문자열 `""`은 통과 |
| `@NotEmpty` | null/빈 문자열 아님 | 공백 `" "`은 통과 |
| `@NotBlank` | null/빈 문자열/공백만 아님 | 문자열은 대부분 이걸 쓴다 |
| `@Size(min, max)` | 길이 | null은 통과(그래서 `@NotBlank`와 함께) |
| `@Email` | 이메일 형식 | 느슨한 검사. 실제 존재 확인은 아니다 |
| `@Pattern` | 정규식 | null은 통과 |

### 비밀번호 정규식 해부

```
^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]{8,64}$
 │  └ 영문이 하나 이상 있어야 함 (전방탐색)
 │              └ 숫자가 하나 이상 있어야 함
 │                         └ ASCII 출력 가능 문자(공백 제외)로 8~64자
 └ 문자열 처음부터 끝까지 검사
```

글자 수 상한을 64로 둔 것은 BCrypt의 72바이트 한계(03강) 때문이다. 바이트와 글자 수가 같아지도록 ASCII로 제한했다.

## 4. 검증은 어디서 하나

| 층 | 검증 | 예 |
| --- | --- | --- |
| DTO (`@Valid`) | **형식**: 비어 있나, 길이, 모양 | 이메일 형식, 비밀번호 규칙 |
| 서비스 | **업무 규칙**: DB를 봐야 아는 것 | 이메일 중복 |

중복 이메일은 DTO 어노테이션으로 검사할 수 없다(DB 조회 필요). 서비스에서 `BusinessException`으로 처리한다. 두 층을 섞지 않는다.

그리고 **프런트(Flutter)에서 검증해도 서버 검증은 반드시 필요하다.** 앱을 거치지 않고 직접 API를 호출할 수 있기 때문이다. 서버는 클라이언트를 믿지 않는다.

## 5. 변환 위치

엔티티 → DTO 변환은 `MemberResponse.from(member)` 정적 팩토리로 DTO 안에 두었다. 규모가 커지면 매퍼 클래스로 뺄 수 있지만 지금은 한 줄이라 과하다.

## 직접 해 보기

1. `curl`로 이메일 형식이 틀린 값과 짧은 비밀번호를 같이 보내 보자. 응답의 `message`에 필드별 오류가 어떻게 합쳐지는가?
2. `SignUpRequest`에서 `@NotBlank`를 지우고 `nickname`을 빼고 보내면 어떻게 되는가? `@Size`만 있으면 null이 통과하는 것을 확인해 보자.
3. 비밀번호에 한글을 넣어 보자. 어느 규칙이 막는가? 이 규칙이 없다면 BCrypt에서 어떤 일이 생길 수 있는가?
4. 생각해 보기: 3단계에서 반려동물 등록 요청에 `memberId` 필드를 넣어 받으면 어떤 문제가 생기는가? (힌트: 누구의 반려동물로 등록되나?)
