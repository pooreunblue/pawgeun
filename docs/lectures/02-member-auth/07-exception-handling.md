# 07. 예외 설계와 @RestControllerAdvice

> 이 강의 한 줄 요약: **예외 처리는 "고객 불만 접수 센터" 하나로 모으는 일이다. 각 부서는 문제가 생기면 "불만 코드"만 적어 올리고, 센터가 고객에게 정해진 양식으로 답한다.**

## 1. 목표: 실패할 때도 일정한 모양으로

### 비유: 택배 배송 조회 상태

택배 조회 화면을 떠올리자. 문제가 생기면 "주소 불명", "수취인 부재", "파손" 같은 **정해진 상태 문구**가 뜬다. 기사님마다 제멋대로 "집에 아무도 없던데요 ㅠ", "주소가 이상함" 같은 문장을 쓰면, 쇼핑몰 시스템은 무슨 일인지 자동으로 판단할 수 없다.

API도 실패할 때 **일정한 모양**으로 답해야 앱이 처리할 수 있다.

```json
{ "code": "EMAIL_DUPLICATED", "message": "이미 가입된 이메일입니다." }
```

| 구성 | 역할 | 택배 비유 |
| --- | --- | --- |
| HTTP 상태 코드 | 큰 분류 (400, 401, 404, 409 ...) | "배송 실패"라는 큰 분류 |
| `code` | 앱이 분기하는 값. 한 번 정하면 바꾸지 않는다 | 시스템용 상태 코드 `ADDR_UNKNOWN` |
| `message` | 사람이 읽는 설명. 문구는 바꿔도 된다 | 고객에게 보이는 "주소를 확인해 주세요" |

앱은 **문장(`message`)을 파싱하지 않고 `code`로 판단**한다. 문구를 "이미 가입된 이메일입니다"에서 "이미 사용 중인 이메일이에요"로 바꿔도 앱은 안 깨진다. 반대로 `code`를 바꾸면 앱의 `if (code == "EMAIL_DUPLICATED")`가 깨진다. 그래서 `code`는 한 번 정하면 계약처럼 지킨다.

## 2. 구성 요소

```text
ErrorCode               오류 종류 목록 (enum): 상태 코드 + 기본 메시지     ← 불만 코드표
BusinessException       "예상 가능한 실패"를 알리는 예외 (ErrorCode를 담음) ← 불만 접수 쪽지
ErrorResponse           응답 본문 (code, message)                         ← 고객 답변 양식
GlobalExceptionHandler  예외 → ErrorResponse 변환                         ← 불만 접수 센터
```

서비스는 이렇게 **쪽지만 던진다.**

```java
// 서비스는 HTTP를 모른다. "이메일 중복"이라는 불만 코드만 적어서 던진다
throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
```

서비스는 HTTP를 모른다. 409라는 숫자도, JSON 모양도 모른다. 상태 코드와 JSON으로 바꾸는 일은 핸들러가 한다.

비유: 주방장(서비스)은 "재료 소진"이라는 쪽지만 홀로 보낸다. 손님에게 어떤 말투로 사과할지는 홀 매니저(핸들러)가 정한다. 주방장이 손님 응대까지 하면 같은 주방을 배달 앱(스케줄러, 배치 등 HTTP가 아닌 곳)에서 쓸 때 곤란해진다. 서비스가 `ResponseEntity`를 만들지 않으니 같은 서비스를 어디서 불러도 문제없다.

새 오류가 생기면 enum에 한 줄 추가하면 끝이다.

## 3. @RestControllerAdvice

```java
@RestControllerAdvice                              // 모든 컨트롤러의 예외를 여기서 받는다
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)     // BusinessException이 오면 이 메서드가 처리
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) { ... }
}
```

모든 컨트롤러에서 던져진 예외를 **한곳에서** 잡는다. 컨트롤러마다 `try/catch`를 쓰지 않는다.

비유: 매장마다 각자 불만 접수 창구를 두면 응대 품질이 제각각이 된다. **본사 고객센터 하나**로 모으면 모든 매장의 불만이 같은 양식으로 처리된다. Django의 미들웨어/예외 핸들러와 비슷한 자리다.

### 핸들러 우선순위: 가장 구체적인 담당자에게

예외가 던져지면 **가장 구체적인 타입**의 핸들러가 선택된다. 고객센터에 "환불 전담", "배송 전담" 직원이 있으면 해당 전담 직원이 받고, 아무도 해당되지 않으면 일반 상담원이 받는 것과 같다.

| 던져진 예외 | 잡는 핸들러 | 응답 | 고객센터 비유 |
| --- | --- | --- | --- |
| `BusinessException` | `handleBusiness` | `ErrorCode`의 상태/메시지 | 업무 불만 전담 |
| `MethodArgumentNotValidException` | `handleValidation` | 400 `INVALID_INPUT` + 필드별 메시지 | 서류 기재 오류 전담 |
| `HttpMessageNotReadableException` | `handleUnreadable` | 400 `INVALID_INPUT` (JSON 문법 오류 등) | "글씨를 못 알아보겠어요" 전담 |
| 그 밖의 모든 `Exception` | `handleUnexpected` | 500 `INTERNAL_ERROR` | 일반 상담원 (최후의 창구) |

## 4. 마지막 방어선: 500은 내용을 숨긴다

```java
log.error("처리되지 않은 예외", e);                    // 서버 로그에는 전체 스택 트레이스
return ... ErrorResponse.of(ErrorCode.INTERNAL_ERROR); // 응답에는 고정 문구만
```

### 비유: 비행기 결항 안내

비행기가 결항되면 승객에게는 "기체 점검으로 결항되었습니다"라고만 안내한다. "3번 엔진 연료 펌프의 2번 밸브가..." 같은 정비 기록은 **정비팀 내부 문서**에만 남긴다.

스택 트레이스, SQL, 클래스 이름은 공격자에게 **지도**가 된다. "아, 이 서버는 PostgreSQL에 Hibernate를 쓰고, 이 쿼리에서 터지는구나." 그래서 응답에는 싣지 않고 로그에만 남긴다. 개발자는 로그를 보고 고치면 된다.

## 5. 예외 설계 기준

- **예상 가능한 실패**(중복 가입, 없는 회원, 권한 없음)는 `BusinessException`으로. 정상적인 업무 흐름의 일부다. 식당에서 "재료가 떨어졌어요"는 사고가 아니라 있을 수 있는 일이다.
- **예상 못 한 실패**(버그, DB 다운)는 그대로 터뜨려 500과 로그로 남긴다. 업무 예외로 감싸 숨기지 않는다. 주방에 불이 났는데 "재료 소진"이라고 안내하면 아무도 불을 끄러 오지 않는다.
- **예외를 삼키지 않는다.** `catch (Exception e) {}`는 화재 경보기의 배터리를 빼 버리는 것과 같다. 조용해지지만 문제는 그대로 자란다.
- **보안:** 로그인 실패는 `LOGIN_FAILED` 하나로 통일했다(03강).

## 6. 필터에서 난 오류는 별개다

`@RestControllerAdvice`는 **DispatcherServlet 안의 MVC 요청 처리 중**에 난 예외를 잡는다. 컨트롤러 메서드 안에서 난 예외만이 아니다. 예를 들어 JSON 문법이 깨진 본문은 컨트롤러 메서드를 **호출하기 전**, 요청 본문을 `SignUpRequest`로 변환하는 단계에서 `HttpMessageNotReadableException`이 나는데, 이것도 MVC 처리의 일부라 `GlobalExceptionHandler.handleUnreadable`이 처리한다(405·415·404도 마찬가지).

반면 인증 필터(Security 필터 체인)는 DispatcherServlet보다 **앞**에서 동작한다. MVC 처리에 들어가기 전이라, 여기서 나는 401/403은 핸들러가 못 본다.

```text
요청 → [Security 필터 체인] → DispatcherServlet → 컨트롤러 → 서비스
        ↑ 여기 오류는                  ↑ 여기부터 오류는
        EntryPoint/AccessDeniedHandler  @RestControllerAdvice가 처리
```

비유: 본사 고객센터는 **매장 안**에서 생긴 불만만 받는다. **주차장 입구**(필터)에서 생긴 문제는 주차 관리실이 직접 처리한다. 다만 두 곳이 **같은 답변 양식**(`ErrorCode`, 같은 JSON 모양)을 쓰기로 약속했기 때문에, 고객(앱) 입장에서는 어디서 막혔든 같은 모양의 답을 받는다(04강).

## 7. 아직 안 한 것

- `DataIntegrityViolationException`(DB 제약 위반): 동시 가입 경합은 `MemberService`에서 409로 변환한다(05강). 405/415/404도 전용 핸들러로 처리한다.
- 요청 추적 id, 오류 로그 알림: 운영 단계에서.

## 직접 해 보기

1. `curl`로 JSON 문법이 깨진 본문(`{"email":`)을 보내 보자. 어느 핸들러가 응답하는가?
2. `MemberService.getById`가 던지는 `MEMBER_NOT_FOUND`의 상태 코드를 `404`에서 `410`으로 바꿔 보자. 서비스 코드는 고쳐야 하는가?
3. 일부러 컨트롤러에서 `throw new IllegalStateException()`을 던져 보자. 응답과 서버 로그가 각각 어떻게 다른가?
4. 새 오류 `NICKNAME_TOO_OFFENSIVE`(400)를 만들려면 몇 곳을 고쳐야 하는지 세어 보자.
