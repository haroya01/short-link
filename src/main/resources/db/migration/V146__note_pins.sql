ALTER TABLE note
  ADD COLUMN pinned_at DATETIME(6) NULL,
  ADD KEY idx_note_user_pinned (user_id, pinned_at);
