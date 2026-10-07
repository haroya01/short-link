-- SENSITIVE is a reserved word in MySQL 8, hence marked_sensitive.
ALTER TABLE note
  ADD COLUMN content_warning VARCHAR(100) NULL,
  ADD COLUMN marked_sensitive BOOLEAN NOT NULL DEFAULT FALSE;
