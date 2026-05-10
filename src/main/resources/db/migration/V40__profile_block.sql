CREATE TABLE profile_block (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  block_type VARCHAR(16) NOT NULL,
  content VARCHAR(120) NULL,
  profile_order INT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  INDEX idx_profile_block_user_order (user_id, profile_order)
);
