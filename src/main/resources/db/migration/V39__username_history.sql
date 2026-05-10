CREATE TABLE username_history (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  old_username VARCHAR(32) NOT NULL,
  changed_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  INDEX idx_username_history_lookup (old_username, expires_at),
  INDEX idx_username_history_user (user_id)
);
