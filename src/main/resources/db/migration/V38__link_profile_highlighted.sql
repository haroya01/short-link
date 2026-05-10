ALTER TABLE link
  ADD COLUMN profile_highlighted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_link_profile_highlight ON link (user_id, profile_highlighted);
