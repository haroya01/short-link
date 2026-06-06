-- Dedicated "last edited" timestamp for posts. Distinct from updated_at (a Hibernate
-- @UpdateTimestamp bumped by any row write, including the per-read view-count save) and from
-- published_at. Set only by the edit use-cases (metadata / body blocks / revision restore) via
-- PostEntity.markEdited(), so the reader can show a trustworthy "수정 {date}" hint.
--
-- Nullable, no backfill: existing posts read as "never edited since publish" (NULL) → no hint, which
-- is the correct behaviour for already-published content. The column fills in the first time an
-- author edits a post after this ships.
ALTER TABLE posts ADD COLUMN last_edited_at DATETIME(6) NULL AFTER published_at;
