ALTER TABLE users
  ADD COLUMN deleted_at DATETIME(3) NULL,
  ADD KEY idx_users_deleted_at (deleted_at);
