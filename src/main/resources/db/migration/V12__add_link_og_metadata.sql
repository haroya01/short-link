ALTER TABLE link
  ADD COLUMN og_title VARCHAR(300) NULL,
  ADD COLUMN og_description VARCHAR(800) NULL,
  ADD COLUMN og_image VARCHAR(1024) NULL,
  ADD COLUMN og_fetched_at DATETIME(6) NULL,
  ADD COLUMN og_fetch_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

CREATE INDEX idx_link_og_fetch_status ON link (og_fetch_status, og_fetched_at);
