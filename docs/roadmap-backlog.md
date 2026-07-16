# Roadmap backlog — what's next

Snapshot after the Medium-parity roadmap landed (절제 패스 → 소셜 하이라이트 → 팔로잉/팔로워 목록 →
읽기 기록 → For You, all merged across backend / web / iOS). This is the menu to pick from next,
grouped by axis. Not committing anyone to anything — a place to choose from.

## Design

- **절제 패스 심화 (deeper restraint pass)** — the "학생 앱" diagnosis: raw `font(.system(size:))`
  hand-set per screen (bypassing the `typeScale` tokens), green + glass over-used, density too high,
  decoration winning over content. The first pass proved it on the analytics screens; the deepening
  applies it app-wide: one type scale enforced, green only for the primary action / data, more
  whitespace, glass on 1–2 floating chrome surfaces only. (kurl-ios, within AGENTS.md §10 "조용한 웹로그".)
  **← chosen as the next step.**
- **Design-review backlog** — 63 findings; batches 1–4 shipped (#682). Remaining includes
  **IA fragmentation** (information-architecture consistency) and the lower-severity tail.
- **Mobile follow-ups** — `p/` surface tab bar, carousel peek (deferred from the mobile overhaul).

## Product / features (remaining Medium gaps)

- **Highlight → author notification** — small follow-up; notify the author when someone highlights
  their writing. Touches the notification subsystem (NotificationType, BlogInteractionEvent, listener).
- **Reading-history privacy notice** — legal; reading history is PII, so the privacy policy should
  disclose collection before it drives anything at scale.
- Larger gaps: **post URL import + canonical**, **TTS "듣기"**, **digest email / newsletter**
  (blocked on mail infrastructure), **membership** (needs a product decision).

## Engineering depth (portfolio-leaning)

- **Recommendations v2 → v3 → v4** — heuristic ranking + eval harness → item–item collaborative
  filtering → two-tower embeddings + learned ranker. Full plan in
  [`docs/recommendations-roadmap.md`](./recommendations-roadmap.md). Highest portfolio impact; the
  owner intends to build these.
- **e2e tests for the new interactions** — highlight selection, follow modal/list, reading history,
  For You tab. These can't be verified headlessly today; e2e (Playwright / XCUITest) would let CI
  catch regressions every run.
- **Backend perf** — the full-review HIGH items + candidates #9–#18 (see `perf-notes/`).

### Correctness / concurrency (confirmed defects — good "found & fixed" stories)

Both surfaced while deriving interview questions from the redirect + auth hot paths
(see `docs/interview/`). Small diffs, high signal.

- **Refresh rotation race — `exists → delete` is not atomic.** `AuthService.refresh()` gates on
  `refreshStore.exists(userId, jti)` then *separately* `delete()`s. Two concurrent refreshes with
  the same `jti` (two subdomains `blog.` + apex, or two tabs) both pass `exists` before either
  deletes → both take the live-session branch → **one refresh token yields two valid sessions**
  (one-time-use broken), and reuse-detection is blind in the race window. The grace marker only
  covers the *sequential* replay, not this *simultaneous* one. **Fix:** gate on the atomic result of
  the delete, not a prior `exists` — `RedisRefreshTokenStore.delete` returns `void` today; have it
  return the `DEL` count (`redis.delete` → `Long`) so only the request whose delete returns 1 is the
  rotator; the loser falls through to the grace/reissue branch. (`GETDEL` / Lua = same idea.)
- **Prefetch & blocked-country burn a capped view.** `LinkRedirectFlow.execute()` runs
  `enforceViewLimit()` (atomic increment, committed) *before* the country-block check and *before*
  the prefetch classification. So a browser prefetch or a blocked-country hit consumes one of the
  `maxViews` slots even though no human was ever redirected — inconsistent with the stats path,
  which excludes prefetch from human clicks using the same `Sec-Purpose`/`Purpose`/`X-moz` signal.
  **Fix:** classify prefetch + resolve the country block *before* incrementing, and only spend a
  view on a real, non-prefetch, allowed redirect. Ordering caveat: keep the increment itself atomic
  (`UPDATE … WHERE view_count < max_views`) — deferring it past the redirect decision reopens the
  oversell race.

---

Suggested order if optimising for portfolio: **recommendations v2** (infra-free, big perceived win,
and the measurement baseline for v3/v4). If optimising for product polish: **절제 패스 심화** (light
relative to impact). The owner picked the restraint pass to go next.
