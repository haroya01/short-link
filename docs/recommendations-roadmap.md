# Recommendations Roadmap — from rules to a learned feed

The "For You" feed shipped as a **rule-based v1**: candidates = recent published posts carrying the
reader's interest tags (explicit tag follows ∪ tags of what they read/liked, minus hidden), ordered
by recency, with a trending cold-start fallback. That is one source feeding one stage of what is
normally a multi-stage system. This doc lays out the path from there to a feed that *learns* what a
reader will engage with — the way Instagram / YouTube actually work — in three phases that can each
ship and prove value independently.

It is a **plan**, not a spec to implement now. Each phase is scoped so it stands alone as an
engineering story (useful for a portfolio) and so we never take on ML infrastructure before the
heuristics stop paying off.

---

## The mental model: a funnel, not a sort

Large-scale recommendation is almost always a **two-or-three-stage funnel**, because scoring every
candidate with an expensive model is infeasible:

```
all published posts ──① candidate generation──▶ ~hundreds ──② ranking──▶ ~tens ──③ re-ranking──▶ feed
                         (cheap, high recall)               (costly, precise)        (policy/diversity)
```

- **① Candidate generation** — cheaply pull a few hundred "plausibly relevant" posts from several
  *sources*. v1's tag match is one source. Real systems union many: follows, co-engagement,
  embedding nearest-neighbours, trending, fresh.
- **② Ranking** — score each candidate with `P(the reader engages)`. This is where "learning" lives.
- **③ Re-ranking / policy** — what a raw score can't express: diversity (no 10 posts from one
  author), freshness, exploration, already-seen removal, integrity/safety.

v1 has only ① (one source) + a recency sort. The phases below add ②, then a second ① source, then
replace the heuristics with learned models.

### Where it lives (hexagonal)

| Concern | Slice |
| --- | --- |
| Candidate sources, ranking, re-ranking orchestration | `post.application.read` (a `ForYouQueryService` that grows) |
| Co-occurrence / embedding tables, model artifacts | `post.infrastructure.persistence` + new read-model tables (Flyway) |
| Offline batch jobs (CF matrix, embedding refresh) | a scheduler in `post` (or a separate worker) — **owner-run infra** |
| Event log (the training substrate) | already partly here: `post_read`, `post_like`, `user_follow`, `user_tag_pref` |
| HTTP surface | `GET /api/v1/feed/for-you` stays stable across phases — clients don't change |

A deliberate invariant: **the endpoint contract (`PublicFeedView`) does not change across phases.**
The web/iOS clients already render it. All the evolution is server-side.

---

## The data foundation (already being logged)

Every phase runs on the same fuel, and we are already collecting it:

- `post_read` — what you actually read (implicit, the strongest signal).
- `post_like` / `post_bookmark` — explicit positive signals.
- `user_follow` / `series_subscription` / `user_tag_pref` — declared interests.
- `post_view_event` — anonymous/aggregate traction (trending).

**Implicit signals (read, dwell, skip) ≫ explicit (like).** The single highest-leverage thing we can
do *regardless of phase* is widen and enrich this log — e.g. record read **depth/dwell** (not just a
read happened), and an explicit **"not interested"** negative. Models are only as good as the labels;
the log is the product. (Privacy: this is per-user behavioural data — see *Cross-cutting → Privacy*.)

---

## Phase v2 — heuristic ranking (no ML)

**Goal:** add stage ② as a transparent scoring function. Stop ordering by recency; order by a score.

**Score sketch** (per candidate, all interpretable):

```
score = w_affinity · tagAffinity(reader, post)      // how many interest tags match, weighted
      + w_fresh    · recencyDecay(post.publishedAt)  // exp decay over hours/days
      + w_quality  · engagementRate(post)            // likes+reads per impression, smoothed
      − w_seen     · staleness(reader, post)         // already-read / shown-before penalty
```

**Why it matters:** most of the "it knows me" feeling comes from here, *without* a model. It's the
classic learning-to-rank baseline (a hand-tuned linear scorer) and it makes the later ML phase
measurable — you can't tell if a model is better than nothing until "nothing" is a real baseline.

**Engineering:** extend `ForYouQueryService` to fetch a larger candidate pool (still tag-sourced),
compute the score in-app, sort, paginate over the ranked list. Add `engagementRate` as a cheap
denormalised column or a small rollup. No new infra.

**Evaluation:** introduce **offline metrics** here even though it's heuristic — hold out the last N
reads per user, ask "would this scorer have ranked them highly?" (recall@k, nDCG@k). This harness is
reused by every later phase. Then a small **online A/B** (recency vs scored) on engagement.

**Portfolio angle:** demonstrates the funnel, learning-to-rank intuition, and an evaluation harness —
the unglamorous-but-essential foundation most people skip.

---

## Phase v3 — collaborative filtering (the first "learned" signal)

**Goal:** a second candidate source that captures taste tag-matching can't: *"readers who read X also
read Y."* This connects posts that share an audience even when they share no tags.

**Approach (item–item CF, the pragmatic one):**

- Batch job builds a **co-occurrence**: for each pair of posts, how many readers read both
  (within a window), normalised (cosine / Jaccard / shifted-PMI) to dampen popularity.
- Store top-K neighbours per post in a `post_similar(post_id, neighbour_id, score)` table.
- New candidate source: take the reader's recent reads → union their stored neighbours → feed the
  funnel alongside the tag source.

**Why item–item, not user–user:** items are more stable than users, the neighbour table precomputes
offline and serves with a cheap lookup, and it cold-starts a *user* instantly (one read → neighbours).

**Engineering:** a scheduled batch job (nightly) materialises `post_similar`; `ForYouQueryService`
gains a `CoEngagementCandidateSource`. The v2 ranker now scores a richer, more surprising candidate
set. This is the first piece of **owner-run infra** (a batch job over the event log).

**Evaluation:** same offline harness; expect recall to jump on users whose taste crosses tags.

**Portfolio angle:** classic CF done right — the popularity-normalisation and item-item choice are
exactly the judgment calls interviewers probe.

---

## Phase v4 — embeddings + a learned ranker (the full stack)

**Goal:** replace hand-tuned pieces with learned ones. Two halves:

1. **Retrieval — two-tower embeddings.** A *user tower* and an *item tower* learn vectors in a shared
   space so a user's vector is close to items they'll engage with. Candidate generation becomes an
   **approximate nearest-neighbour** lookup of the user vector against an item-vector index (FAISS /
   pgvector). This generalises CF: it places *new* items by their content features (cold-start) and
   captures higher-order similarity.
2. **Ranking — a learned model.** Replace v2's linear scorer with a model trained on the event log:
   start with **logistic regression / GBDT** (strong, cheap, interpretable), only later a neural
   ranker. Multi-objective: predict read, dwell, like, save, "not interested" — blend with tuned
   weights (this is the "watch-time not clicks" lever).

**The real cost is the platform, not the model:**

- **Event logging** → a clean training-example pipeline (impressions + labels, with the *negatives* —
  what was shown and skipped, not just what was engaged).
- **Feature store** so training and serving compute the same features (train/serve skew is the
  classic bug).
- **Offline eval → online A/B** loop, with guardrail metrics, before anything ships.
- **Model serving** (low-latency scoring) + periodic retrain.

**Honest scope note:** v4 is a genuine infrastructure investment and is gated on **data volume** —
a small blog may not have enough labelled events for a neural ranker to beat v2+v3. The owner-run
infra constraint applies (training/serving/A/B platforms are operational scope, not app code).
Treat v4 as "build the platform, start with GBDT, earn the neural step with data."

**Portfolio angle:** the end-to-end ML system — retrieval/ranking split, embeddings, the
train/serve-skew and offline/online eval problems, and the maturity to say *when* deep learning is
and isn't worth it.

---

## Cross-cutting (every phase)

- **Exploration vs exploitation.** A pure exploit feed never learns about new items/interests and
  collapses into a bubble. Reserve a slot for uncertain/fresh items (ε-greedy → bandit). This is a
  *learning* mechanism, not just UX nicety.
- **Diversity & de-dup (stage ③).** Cap per-author/per-topic runs; the score maximiser alone produces
  monotonous feeds.
- **Integrity / safety.** "Predicts engagement" and "healthy feed" are different objectives —
  engagement-optimisation amplifies the inflammatory. Demote borderline content explicitly; don't let
  the ranker discover it's "engaging."
- **Cold start.** New reader → trending (v1, done) / onboarding interests. New post → content
  features / embeddings (v4). Always have a non-personalised floor.
- **Feedback-loop hygiene.** The model trains on data the model produced (presentation bias). Log
  impressions, not just engagements, and consider inverse-propensity correction so the flywheel
  doesn't just reinforce yesterday's ranking.
- **Privacy.** This is per-user behavioural data (PII). Honour account-deletion wipe (already done for
  `post_read`/`post_highlight`), keep the reading-history controls (clear/forget), and update the
  privacy policy before this data drives anything user-visible at scale. See the legal backlog.

---

## Sequencing & ROI

| Phase | Adds | Infra cost | "Feels personalised" payoff |
| --- | --- | --- | --- |
| v1 (done) | tag candidates + recency | none | low–medium |
| v2 | heuristic ranking + eval harness | low | **high** |
| v3 | co-engagement candidate source | medium (batch job) | high |
| v4 | embeddings retrieval + learned ranker | high (ML platform) | high, data-gated |

The honest read: **v2 + v3 deliver most of the perceived "it knows me"** without ML infrastructure;
v4 is where it becomes a *learned* system and where the portfolio depth (and the cost) lives. Do them
in order — each makes the next measurable.
