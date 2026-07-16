# 면접 문답 — 리다이렉트 핫패스

> 소재: URL 단축기의 `GET /{shortCode}` 리다이렉트 경로 한 곳.
> 표면은 달라 보이는 두 질문이지만, 파고들면 **동시성 정확성**과 **트랜잭션/커넥션 수명**이라는 백엔드 근본에서 만난다.

관련 코드:
- `link/redirect/application/LinkRedirectFlow.java`
- `link/application/write/IncrementViewCountUseCase.java`
- `link/infrastructure/persistence/JpaLinkRepository.java`
- `resources/application.yml` (`spring.jpa.open-in-view: false`)

---

## Q1. `maxViews` 하드 리밋을 동시성 하에서 정확히 지키기

### 질문
링크에 `maxViews = 10` (조회 10회 후 만료)이 걸려 있다. 같은 링크로 요청 100개가 동시에 쏟아질 때, **정확히 10번만** 통과하고 나머지는 만료 처리되게 하려면 조회수 증가 + 한도 체크를 어떻게 구현할 것인가?

그리고 — 우리는 브라우저 프리페치/소셜 미리보기 크롤러를 "사람 클릭"에서 제외해 통계를 정직하게 유지한다. 그 한도 로직에서 **프리페치 요청**이 들어오면 `maxViews` 카운터는 어떻게 되어야 하는가?

### 핵심 함정 — 카운터가 사실 두 개다
| | 클릭 통계 | `maxViews` 한도 |
|---|---|---|
| 성격 | 대시보드용 집계 | 돈 걸린 비즈니스 약속(선착순 N명) |
| 정확성 | 1~2개 유실 OK | **정확히 N, 오버셀 금지** |
| 구현 | Redis 버퍼 + 배치 flush | **DB 원자적 조건부 UPDATE** |

이 둘을 섞으면 안 된다. Redis 배치 방식(중복제거 후 DB로 flush)은 **클릭 통계**엔 정답이지만, 하드 리밋에 쓰면 flush 창(예: 5초) 동안 DB의 `view_count`가 옛 값이라 그 사이 요청이 다 새어나가 **오버셀**된다.

### 모범답안 — 경쟁을 앱이 아니라 DB 락에 떠넘긴다

```sql
UPDATE link
   SET view_count = view_count + 1
 WHERE id = ? AND view_count < max_views
```

- 이 문장의 **rows-affected** 를 반환값으로 쓴다.
  - `1` → 자리 획득, 리다이렉트 진행
  - `0` → 이미 한도 도달, 만료 처리
- **왜 100개 동시 요청에도 정확히 10개만 통과하나:** DB가 해당 링크 **한 행에 락**을 걸어 UPDATE들을 직렬화한다. 각 UPDATE가 `view_count < max_views` 체크와 `+1`을 **원자적으로 한 방에** 수행 → 딱 10개만 조건 통과, 11번째부터는 `view_count = 10`이라 WHERE에 안 걸려 `0` 반환. 앱 락도, Redis도, 경쟁 창도 없다.

실제 구현 (`JpaLinkRepository`):
```java
@Modifying(clearAutomatically = true)
@Query("UPDATE LinkEntity l SET l.viewCount = l.viewCount + 1 "
     + "WHERE l.id = :linkId AND (l.maxViews IS NULL OR l.viewCount < l.maxViews)")
int incrementViewCountIfBelowLimit(@Param("linkId") Long linkId);
```

### 흔한 오답 — TOCTOU 경쟁
```
SELECT view_count  →  앱에서 "9 < 10? OK"  →  UPDATE view_count + 1
```
100개 스레드가 **동시에 9를 읽고, 다 통과라 착각하고, 다 쓴다** → 조회수 109. 체크와 쓰기 사이의 틈(**TOCTOU: Time-Of-Check to Time-Of-Use**)에서 터진다. "정합성은 트레이드오프로 포기한다"가 아니라, **경쟁을 DB 행 락에 위임하면 공짜로 정확**해진다.

### 프리페치 판정
현재 코드는 **한도 증가가 프리페치 판별보다 먼저** 실행된다 → 사용자가 누르지도 않은 브라우저 프리페치가 유료 조회 1회를 태운다. 반면 통계 쪽은 같은 신호(`Sec-Purpose` / `Purpose` / `X-moz` 헤더)로 프리페치를 골라내 사람 클릭에서 뺀다.

**→ 통계에선 구분해서 빼면서 한도에선 안 빼는 비대칭 = 아무도 눈치 못 챈 구멍.**

> 면접에서 최고의 답: *"통계에선 프리페치를 구분해 빼는데 한도에선 안 빼네요. 일관성 없습니다 — 저라면 플래그로 올리겠습니다."*
> **문제를 발견하는 게 푸는 것보다 점수가 높다.**

### 꼬리질문
- 증분을 리다이렉트 확정 시점 뒤로 미루면 프리페치/차단 문제는 풀리지만 원자성이 깨져 오버셀이 난다. "정확한 한도"와 "정확한 소비 대상"의 트레이드오프를 어디서 끊을 것인가?
- `maxViews = 10`이 "방문자 10명(unique)"인가 "총 리다이렉트 10번"인가? IP 중복제거를 넣는 순간 의미가 바뀐다. "선착순 쿠폰 10명"이면 어느 정의가 맞나?

---

## Q2. `spring.jpa.open-in-view: false` 로 끈 이유와 그 여파

### 질문
(a) OSIV를 왜 껐나?
(b) 껐더니 리다이렉트의 조회수 `UPDATE` 쿼리가 예외로 터졌다. 왜이고, 어떻게 고치나?

### (a) 왜 껐나 — 커넥션 풀 고갈
- **OSIV(Open Session In View)** = "Session(=Hibernate 영속성 세션 = DB 커넥션)을 View(응답 렌더)까지 열어둘 것인가" 스위치.
- `true`면 요청 하나가 **응답 끝까지 DB 커넥션을 쥔다.**
- 우리는 **SSE**(실시간 스트리밍) 엔드포인트를 운영 — 커넥션이 **5분씩** 열려 있다. OSIV가 켜져 있으면 이 5분짜리 스트림이 그동안 **DB 커넥션 하나를 놀리면서 계속 점유**한다.
- 그런 요청 수십 개가 겹치면 **커넥션 풀 고갈** → 정작 리다이렉트 같은 짧은 요청이 커넥션을 못 받아 죽는다. (이 프로젝트가 실제로 겪은 장애.)
- 그래서 OSIV를 끄고, **트랜잭션 경계 안에서만** 커넥션을 쥐게 바꿨다.

### (b) 왜 UPDATE가 터졌나 — 사라진 주변 트랜잭션
- OSIV를 끄면 요청 전체를 감싸는 세션/트랜잭션이 **없다.**
- `@Modifying` UPDATE는 **활성 트랜잭션이 필수** → 도달 시점에 트랜잭션이 없어 `TransactionRequiredException`. `maxViews` 걸린 모든 링크의 리다이렉트가 죽는다.
- **해결:** 해당 유스케이스에 `@Transactional`을 명시해 **자기 트랜잭션 경계를 스스로** 열게 한다.

```java
// redirect 경로는 트랜잭션 없이 진입하는데 @Modifying UPDATE 는 활성 트랜잭션을 요구한다 —
// 이게 빠지면 maxViews 링크의 모든 리다이렉트가 TransactionRequiredException 으로 죽는다.
@Transactional
public int execute(IncrementViewCountCommand command) {
  return repository.incrementViewCountIfBelowLimit(command.linkId().value());
}
```

> 아이러니: OSIV가 켜져 있었으면 이 버그는 숨겨져서 안 드러났다. **끈 결정이 이 실수를 드러낸** 셈.

### 보너스 — `clearAutomatically = true`
벌크 UPDATE는 영속성 컨텍스트를 **우회**해 DB만 바꾼다. 1차 캐시에 managed 상태로 남은 `LinkEntity`는 옛 `view_count`를 그대로 들고 있어 stale. `clearAutomatically`가 컨텍스트를 비워 그 유령 엔티티를 제거한다. (password-unlock 경로는 이미 entity를 로드한 채 넘어오므로 이게 없으면 위험.)

### 꼬리질문 (성능 축)
- 이 핫패스에서 클릭 기록(`ClickRecorder.record()`)이 `@Async`가 아니라 **동기 @Transactional INSERT**로 요청 스레드를 물고 있다. OSIV를 끈 목적이 커넥션 점유 최소화였는데, 리다이렉트마다 GeoIP·ASN·UA 분류 후 동기 INSERT를 하는 건 그 목적과 상충하지 않나?
- 클릭 집계를 비동기/배치로 뺐을 때의 정합성 비용(유실·순서·중복)은?

---

## 한 줄 요약
- **Q1:** Redis 배치 = 클릭 통계(대량·유실OK) / **원자적 조건부 UPDATE = 하드 리밋**(정확·돈걸림). 경쟁은 DB 행 락에 위임하면 공짜로 정확. 프리페치 비대칭은 발견해서 플래그하는 게 정답.
- **Q2:** OSIV off = SSE 커넥션 풀 고갈 방지. 대가로 주변 트랜잭션이 사라지므로 쓰기 경로는 `@Transactional`로 **자기 경계를 스스로** 열어야 한다.
