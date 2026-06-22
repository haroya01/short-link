# query-audit ↔ kurl: 안 잡힘 → 잡힘 (블로그용 raw 자료)

> 글로 다듬기 전 사실/데이터만. 직접 재현·캡처한 실측.

## 배경
- query-audit 는 kurl 테스트에 이미 물려 있었다: `build.gradle` `io.github.haroya01:query-audit-spring-boot-starter:0.3.2`.
- 그런데 `@QueryAudit` 가 붙은 테스트는 8개뿐 — **전부 campaign(7) + link(1) 도메인.** `post` 도메인 커버리지 = 0.
- 즉 post 도메인의 쓰기 N+1 은 도구가 **한 번도 안 본** 코드라 조용히 머지됐다.

## 대상 코드 (프로덕션, 실재 N+1)
`ReplacePostBlocksUseCase.execute()` — 본문 블록 교체:
```java
return postBlockRepository.saveAll(entities);   // 블록 N개
```
`saveAll` 을 쓰니 "배치되겠지" 싶지만 — 실제로는 블록 수만큼 단일 INSERT.

## 안 잡힘 → 잡힘 (config 한 줄 차이, 같은 테스트)
테스트: `post/application/write/ReplacePostBlocksBatchQueryAuditTest` (블록 6개 교체).

쿼리 프로파일 (query-audit 실측, totalQueries=11):
```
1× INSERT users        (시드)
1× INSERT posts        (시드)
1× SELECT posts        (requireOwned)
1× DELETE post_block   (deleteAllByPostId)
6× INSERT post_block   ← saveAll 이 배치 안 되고 6번 단일 INSERT
1× UPDATE posts        (markEdited)
```
query-audit confirmedIssue:
```
[WARNING] repeated-single-insert —
  Single-row INSERT executed 6 times on table 'post_block'.
  Each INSERT causes a separate network round-trip and log flush.
```

- `query-audit.fail-on-detection: false` (report-only) → **BUILD SUCCESSFUL.** 탐지는 했지만 빌드 통과 = 사실상 안 잡힘(CI 그냥 지나감).
- `query-audit.fail-on-detection: true` (gate) → **BUILD FAILED.**
  `ReplacePostBlocksBatchQueryAuditTest > replacingBodyWithSixBlocks() FAILED`
  `java.lang.AssertionError at QueryAuditExtension.java:239`

→ 시각 자료: `01-before-detected.html` (query-audit HTML 리포트, repeated-single-insert 카드).

## 반전 — 흔한 "fix" 가 안 먹힌다
`hibernate.jdbc.batch_size: 50` + `rewriteBatchedStatements=true` 를 켜고 재실행 → query-audit 가 **여전히 6× INSERT** 라고 보고.
- 이유: `PostBlockEntity` 는 `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
- Hibernate 는 **IDENTITY(auto-increment) 엔티티의 INSERT 를 배치하지 못한다** — insert 직후 생성된 키를 즉시 받아야 하므로 묶을 수 없다. `batch_size` 무효.
- 즉 "saveAll + batch_size 면 배치된다" 는 흔한 오해. **도구가 내 헛fix 까지 잡아줬다.**

## 진짜 fix 와 트레이드오프 (해결)
트레이드오프는 추정이 아니라 코드로 확정됐다: `execute()` 의 반환을 **두 엔드포인트가 실제로 소비**한다 —
`PUT /{id}/blocks`, `PUT /{id}/markdown` 둘 다 반환 블록을 `PostBlockView` 로 매핑해 응답하고,
`PostBlockView` 는 `block.getId()` 를 포함한다. 즉 id 를 못 돌려받으면 응답의 `id` 가 null 이 된다.

택한 fix = **계약 보존**: `PostBlockRepository.insertAll()` (JdbcTemplate 단일 multi-row INSERT) 로 6→1,
직후 `findAllByPostIdOrderByBlockOrderAsc` 로 재조회해 생성된 id 를 채워 반환.
- `PostBlockEntity` 의 `created_at`/`updated_at` 은 Hibernate 가 박던 것이라 raw JDBC INSERT 에선 직접 세팅.
- `JpaTransactionManager` 가 JPA 트랜잭션 커넥션을 JdbcTemplate 에 노출 → 같은 tx·같은 커넥션이라 재조회가 방금 넣은 행을 본다.
- 같은 쓰기 N+1 을 쓰던 `RestorePostRevisionUseCase` 도 `insertAll` 로 같이 교체 (반환을 안 쓰므로 재조회 불필요).

실측 (같은 테스트, fix 전→후): **totalQueries 11 → 7**, `repeated-single-insert` **사라짐**.
```
BEFORE: ... delete post_block, 6× insert post_block (소문자=Hibernate), update posts
AFTER:  ... delete post_block, 1× INSERT post_block (대문자=JdbcTemplate multi-row), 1× select post_block(재조회), update posts
```
→ 시각 자료: `02-after-detected.html`, `02-after-report.json`.

### 또 한 번의 반전 — fix 가 새 경고를 깨운다
재조회 SELECT 때문에 query-audit 가 이번엔 `unbounded-result-set` (post_block, LIMIT 없는 SELECT) 를 띄운다.
- 양성(benign): 블록은 `ReplacePostBlocksCommand` 불변식상 ≤500개로 묶여 있고, **이 SELECT 는 원래 읽기 경로
  (`PostQueryService.listBlocks`) 가 쓰던 바로 그 쿼리** — 쓰기 경로에 새로 등장했을 뿐 시스템엔 신규 위험이 아니다.
- 함께 뜨는 `write-amplification`(posts, 인덱스 8개) 는 fix 와 무관한 기존 항목 (before 에도 있었음).
- 교훈: N+1 하나 죽이면 트레이드오프(여기선 +1 SELECT)가 생기고, 도구는 그 트레이드오프까지 정직하게 비춘다.

## 후속(별개)
- post/feed/notification 도메인에 `@QueryAudit` 확장 + `fail-on-detection: true` 로 승격하면 이런 쓰기 N+1 이 다음부턴 CI 에서 막힌다.
- 같은 패턴: 알림 fan-out `RecordBlogNotificationUseCase.recordForEach` 는 진짜 루프-INSERT N+1(수신자 N명당 INSERT).

## 재현 명령
```
./gradlew test --tests "*ReplacePostBlocksBatchQueryAuditTest" -x jacocoTestCoverageVerification
# 리포트: build/reports/query-audit/ReplacePostBlocksBatchQueryAuditTest.html
```
