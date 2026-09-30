ALTER TABLE profile_block
  ADD COLUMN profile_highlighted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_profile_block_highlight ON profile_block (user_id, profile_highlighted);
