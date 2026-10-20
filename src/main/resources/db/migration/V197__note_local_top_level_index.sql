ALTER TABLE note ADD KEY idx_note_local_top_level (remote_actor_id, in_reply_to_id, id);
