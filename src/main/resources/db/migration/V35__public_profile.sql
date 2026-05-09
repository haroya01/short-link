ALTER TABLE users
  ADD COLUMN username VARCHAR(32) NULL,
  ADD COLUMN bio VARCHAR(280) NULL;

CREATE UNIQUE INDEX idx_users_username ON users (username);

ALTER TABLE link
  ADD COLUMN profile_order INT NULL;

CREATE INDEX idx_link_profile ON link (user_id, profile_order);
