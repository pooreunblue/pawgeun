# 07. 예외 설계와 @RestControllerAdvice

## 1. 목표

API는 실패할 때도 **일정한 모양**으로 답해야 앱이 처리할 수 있다.

```json
{ "code": "EMAIL_DUPLICATED", "message": "이미 가입된 이메일입니다." }
```

- HTTP 상태 코드: 큰 분류 (400, 401, 404, 409 ...)
- `code`: 앱이 분기하는 값. 문장을 파싱하지 않고 `code`로 판단한다. 한 번 정하면 바꾸지 않는다.
- `message`: 사람이 읽는 설명. 문구는 바꿔도 된다.

## 2. 구성 요소

```
ErrorCode            오류 종류 목록 (enum): 상태 코드 + 기본 메시지
BusinessException    "예상 가능한 실패"를 알리는 예외 (ErrorCode를 담음)
ErrorResponse        응답 본문 (code, message)
GlobalExceptionHandler  예외 → ErrorResponse 변환
```

서비스는 이렇게 던지기만 한다.

```java
throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
```

HTTP를 모른다. 상태 코드와 JSON으로 바꾸는 일은 핸들러가 한다. 서비스가 `ResponseEntity`를 만들지 않으니 같은 서비스를 다른 곳(스케줄러 등)에서 써도 문제가 없다. 새 오류가 생기면 enum에 한 줄 추가하면 끝이다.

## 3. @RestControllerAdvice

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) { ... }
}
```

모든 컨트롤러에서 던져진 예외를 한곳에서 잡는다. 컨트롤러마다 `try/catch`를 쓰지 않는다. Django의 미들웨어/예외 핸들러와 비슷한 자리다.

### 핸들러 우선순위

예외가 던져지면 **가장 구체적인 타입**의 핸들러가 선택된다.

| 던져진 예외 | 잡는 핸들러 | 응답 |
| --- | --- | --- |
| `BusinessException` | `handleBusiness` | `ErrorCode`의 상태/메시지 |
| `MethodArgumentNotValidException` | `handleValidation` | 400 `INVALID_INPUT` + 필드별 메시지 |
| `HttpMessageNotReadableException` | `handleUnreadable` | 400 `INVALID_INPUT` (JSON 문법 오류 등) |
| 그 밖의 모든 `Exception` | `handleUnexpected` | 500 `INTERNAL_ERROR` |

## 4. 마지막 방어선: 500은 내용을 숨긴다

```java
log.error("처리되지 않은 예외", e);                    // 서버 로그에는 전체 스택 트레이스
return ... ErrorResponse.of(ErrorCode.INTERNAL_ERROR); // 응답에는 고정 문구만
```

스택 트레이스, SQL, 클래스 이름은 공격자에게 정보가 된다. 응답에는 싣지 않고 로그에만 남긴다.

## 5. 예외 설계 기준

- **예상 가능한 실패**(중복 가입, 없는 회원, 권한 없음)는 `BusinessException`으로. 정상적인 업무 흐름이다.
- **예상 못 한 실패**(버그, DB 다운)는 그대로 터뜨려 500과 로그로 남긴다. 업무 예외로 감싸 숨기지 않는다.
- 예외를 **삼키지 않는다.** `catch (Exception e) {}`는 문제를 숨긴다.
- 보안: 로그인 실패는 `LOGIN_FAILED` 하나로 통일했다(03강).

## 6. 필터에서 난 오류는 별개다

`@RestControllerAdvice`는 **컨트롤러 호출 이후** 단계의 예외만 잡는다. 인증 필터(Security 필터 체인)는 그보다 앞이라 여기서 나는 401/403은 핸들러가 못 본다. 그래서 `SecurityConfig`의 `AuthenticationEntryPoint`/`AccessDeniedHandler`가 같은 JSON 모양을 직접 쓴다(04강). 두 곳이 같은 `ErrorCode`를 쓰기 때문에 앱은 모양이 같다고 믿을 수 있다.

## 7. 아직 안 한 것

- `DataIntegrityViolationException`(DB 제약 위반): 동시 가입 경합은 `MemberService`에서 409로 변환한다(05강). 405/415/404도 전용 핸들러로 처리한다.
- 요청 추적 id, 오류 로그 알림: 운영 단계에서.

## 직접 해 보기

1. `curl`로 JSON 문법이 깨진 본문(`{"email":`)을 보내 보자. 어느 핸들러가 응답하는가?
2. `MemberService.getById`가 던지는 `MEMBER_NOT_FOUND`의 상태 코드를 `404`에서 `410`으로 바꿔 보자. 서비스 코드는 고쳐야 하는가?
3. 일부러 컨트롤러에서 `throw new IllegalStateException()`을 던져 보자. 응답과 서버 로그가 각각 어떻게 다른가?
4. 새 오류 `NICKNAME_TOO_OFFENSIVE`(400)를 만들려면 몇 곳을 고쳐야 하는지 세어 보자.
