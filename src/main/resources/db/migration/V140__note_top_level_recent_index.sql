ALTER TABLE note ADD KEY idx_note_reply_created (in_reply_to_id, created_at);
ALTER TABLE note DROP KEY idx_note_reply;
