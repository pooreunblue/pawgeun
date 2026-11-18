# 02. JWT 깊게 보기

JWT(JSON Web Token)는 "누구인지"를 담은 JSON을 **서명해서** 문자열로 만든 것이다. 서버는 이 문자열을 DB에서 찾지 않고 서명만 검증해 신뢰한다.

## 1. 구조: 점 두 개로 나뉜 세 덩어리

```
eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJwYXdnZXVuIiwic3ViIjoiNDIifQ.(서명 값)
└─ Header ─┘ └─── Payload ───┘ └─ Signature ─┘
```

각 덩어리는 Base64URL로 인코딩한 것이다. **암호화가 아니다.** 누구나 디코딩해서 읽을 수 있다.

```bash
# 직접 풀어 보자
echo 'eyJpc3MiOiJwYXdnZXVuIiwic3ViIjoiNDIifQ=' | base64 -d   # 끝의 = 는 패딩. JWT에서는 생략되어 있어 직접 붙여야 한다
# {"iss":"pawgeun","sub":"42"}
```

| 덩어리 | 내용 | 예 |
| --- | --- | --- |
| Header | 서명 알고리즘 | `{"alg":"HS256"}` |
| Payload | 클레임(주장) 모음 | `{"iss":"pawgeun","sub":"42","iat":...,"exp":...}` |
| Signature | 앞 두 덩어리를 비밀키로 서명한 값 | `HMAC-SHA256(header.payload, 비밀키)` |

**결론 1: payload에 비밀번호나 개인정보를 넣지 않는다.** 읽을 수 있기 때문이다. pawgeun는 회원 id만 넣는다.

## 2. 서명이 하는 일

서명은 "내용이 안 바뀌었고, 비밀키를 가진 서버가 만들었다"를 증명한다.

```
공격자가 payload의 sub를 42 → 1 로 바꿈
   → 서명은 원래 payload로 계산된 값이라 더 이상 맞지 않음
   → 서버가 같은 계산을 해 보면 서명이 불일치 → 거부
```

공격자가 서명을 새로 만들려면 비밀키가 있어야 한다. **결론 2: 비밀키가 곧 서버의 신뢰다.** 유출되면 누구나 아무 회원의 토큰을 만들 수 있다.

이 동작은 `JwtProviderTest.내용을_바꾼_토큰은_거부한다`가 그대로 재현한다.

## 3. 클레임

표준 이름이 정해져 있다(등록 클레임).

| 이름 | 뜻 | pawgeun에서 |
| --- | --- | --- |
| `iss` | 발급자 | `"pawgeun"`. 파싱 때 `requireIssuer`로 확인 |
| `sub` | 주체. 누구의 토큰인가 | 회원 id 문자열 |
| `iat` | 발급 시각 | 발급 순간 |
| `exp` | 만료 시각 | 발급 + 30분 |

파싱 라이브러리는 `exp`가 지났으면 알아서 예외를 던진다(`ExpiredJwtException`). 직접 시간 비교 코드를 쓰지 않는다.

## 4. 알고리즘: HS256 vs RS256

| | HS256 (HMAC) | RS256 (RSA) |
| --- | --- | --- |
| 키 | 비밀키 하나로 서명·검증 | 개인키로 서명, 공개키로 검증 |
| 적합 | 서버가 발급과 검증을 둘 다 함 | 발급 서버와 검증 서버가 다름 |
| 단점 | 검증하는 쪽도 비밀키를 알아야 함 | 키 관리가 더 복잡 |

pawgeun는 서버 하나가 발급·검증을 다 하므로 HS256이면 충분하다. HS256 키는 **256비트(32바이트) 이상**이어야 한다. `JwtProvider` 생성자의 `Keys.hmacShaKeyFor`가 짧으면 예외를 던지고, 그러면 서버가 기동 단계에서 죽는다. 약한 키로 조용히 운영되는 것보다 낫다(`JwtProviderTest.키가_256비트보다_짧으면_기동_단계에서_실패한다`).

## 5. 코드 읽기

```java
Jwts.builder()
    .issuer("pawgeun")
    .subject(String.valueOf(memberId))
    .issuedAt(now)
    .expiration(new Date(now.getTime() + 유효시간))
    .signWith(key)          // 키 길이로 HS256/384/512를 자동 선택
    .compact();             // 문자열로 직렬화
```

```java
Jwts.parser()
    .verifyWith(key)        // 서명 검증용 키
    .requireIssuer("pawgeun") // iss가 다르면 거부
    .build()
    .parseSignedClaims(token)   // 서명·만료·형식을 한꺼번에 검증
    .getPayload();
```

`parseSignedClaims`는 **서명된** JWT만 받는다. 서명이 없는 토큰(`alg: none`)을 받으면 거부하는 것이 이 메서드 이름에 들어 있다. 과거에 서명 없는 토큰을 통과시키는 라이브러리 취약점이 있었기 때문에 중요하다.

## 6. 위험과 대응

| 위험 | 설명 | pawgeun의 대응 |
| --- | --- | --- |
| 토큰 탈취 | 가진 사람이 곧 주인 | HTTPS 전제, 유효시간 30분으로 짧게 |
| 비밀키 유출 | 임의 토큰 위조 가능 | 기본값 없이 환경변수 `JWT_SECRET`만 사용(없으면 서버가 안 뜸), 저장소에 키 커밋 금지 |
| 서버가 토큰을 못 끊음 | 탈퇴·정지한 회원의 토큰이 만료 전까지 유효 | 요청마다 회원 존재를 조회(`getById`)하므로 탈퇴 회원은 `MEMBER_NOT_FOUND`. 완전한 폐기는 리프레시 토큰 도입 때 |
| payload 노출 | 누구나 읽음 | 민감정보를 넣지 않음 |

## 7. 액세스 토큰과 리프레시 토큰 (다음에 추가할 것)

토큰이 짧으면 안전하지만 30분마다 재로그인이 번거롭다. 그래서 두 개를 쓴다.

```
액세스 토큰: 짧음(30분). 모든 API 호출에 사용. 탈취돼도 피해 시간이 짧다.
리프레시 토큰: 김(2주). 액세스 토큰 재발급 전용 주소에서만 사용. 서버 DB에 저장해 폐기 가능.
```

로그아웃·분실 시 서버가 리프레시 토큰을 지우면 재발급이 막힌다. 이미 발급된 액세스 토큰은 만료(30분)까지 쓸 수 있으므로 접근은 길어도 그 안에 끊긴다. 즉시 차단하려면 별도의 블랙리스트가 필요하다. 앱을 붙일 때 추가한다.

## 직접 해 보기

1. 로그인 응답의 `accessToken`을 https://jwt.io 에 붙여 넣어 payload를 읽어 보자. 어떤 정보가 보이는가? (민감한 토큰은 외부 사이트에 넣지 말고, 테스트용 토큰으로만 해 볼 것.)
2. `application.properties`의 `access-token-validity`를 `5s`로 바꾸고 로그인한 뒤 6초 후에 `/api/members/me`를 호출해 보자. 어떤 `code`가 오는가?
3. `JwtProviderTest`에서 `provider(SECRET, ...)`의 SECRET을 한 글자 바꿔 보면 어느 테스트가 깨지는가? 왜?
4. 생각해 보기: payload에 `role: ADMIN`을 넣어 인가에 쓰려 한다. 서명이 있으니 안전한가? 권한이 바뀌면 토큰은 어떻게 되는가?
