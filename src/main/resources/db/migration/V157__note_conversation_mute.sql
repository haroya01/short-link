-- A reply joins the conversation of the note it answers; a top-level note leaves this NULL and is
-- its own conversation, so creating one costs no extra write.
ALTER TABLE note ADD COLUMN conversation_id BIGINT NULL AFTER in_reply_to_id;

UPDATE note n
JOIN (
  WITH RECURSIVE chain AS (
    SELECT id, id AS root FROM note WHERE in_reply_to_id IS NULL
    UNION ALL
    SELECT c.id, chain.root FROM note c JOIN chain ON c.in_reply_to_id = chain.id
  )
  SELECT id, root FROM chain WHERE id <> root
) threads ON threads.id = n.id
SET n.conversation_id = threads.root;

CREATE TABLE note_conversation_mute (
  user_id          BIGINT      NOT NULL,
  conversation_id  BIGINT      NOT NULL,
  created_at       DATETIME(6) NOT NULL,
  PRIMARY KEY (user_id, conversation_id),
  CONSTRAINT fk_note_conversation_mute_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
