-- A reply stays one when fk_note_reply sets in_reply_to_id to NULL or its parent never arrived.
-- Earlier replies are found by their parent or, once the parent is gone, by their conversation.
ALTER TABLE note ADD COLUMN reply BOOLEAN NOT NULL DEFAULT FALSE AFTER conversation_id;

UPDATE note SET reply = TRUE WHERE in_reply_to_id IS NOT NULL OR conversation_id IS NOT NULL;
