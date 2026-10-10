-- Order becomes a property of a collection instead of a separate kind: a PATH is an ordered
-- collection. kind stays and is written from ordered, so the previous release still reads a correct
-- value after a rollback; a follow-up drops it once no shipped client sends or reads kind.
ALTER TABLE collection ADD COLUMN ordered BOOLEAN NOT NULL DEFAULT FALSE AFTER visibility;
UPDATE collection SET ordered = TRUE WHERE kind = 'PATH';
