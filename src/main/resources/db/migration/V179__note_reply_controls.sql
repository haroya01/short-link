-- Every note of a thread carries its first note's reply policy, so a page of replies is checked
-- without reading their roots; changing the policy rewrites the thread by conversation_id.
ALTER TABLE note
  ADD COLUMN reply_policy VARCHAR(16) NOT NULL DEFAULT 'EVERYONE',
  ADD COLUMN reply_hidden_at DATETIME(6) NULL,
  ADD KEY idx_note_conversation (conversation_id);
