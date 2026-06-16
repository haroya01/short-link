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

---

Suggested order if optimising for portfolio: **recommendations v2** (infra-free, big perceived win,
and the measurement baseline for v3/v4). If optimising for product polish: **절제 패스 심화** (light
relative to impact). The owner picked the restraint pass to go next.
