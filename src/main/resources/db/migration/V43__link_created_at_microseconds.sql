-- created_at was second-precision TIMESTAMP. cursor pagination encodes (epochMillis, id);
-- second-precision DB values made every row look "older" than a same-second cursor, breaking
-- the predicate. Microsecond precision aligns DB / Java (Instant.now() / cursor encoding).
ALTER TABLE link MODIFY COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
