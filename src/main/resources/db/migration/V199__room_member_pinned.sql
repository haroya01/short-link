-- A member keeps a room at the top of their room list. Per member, so each friend pins their own.
ALTER TABLE room_member ADD COLUMN pinned_at DATETIME(6) NULL;
