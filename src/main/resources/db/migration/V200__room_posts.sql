-- A room post's room, set exactly while posts.audience is ROOM; PostAccessSql asks room_member of
-- it like note.room_id. A post never moves to another room. No foreign key, like note.room_id.
ALTER TABLE posts
  ADD COLUMN room_id BIGINT NULL,
  ADD KEY idx_posts_room (room_id, published_at);

-- The room a comment or highlight was made in, when its post was a room post then. It stays with
-- that room's members after the author takes the post out of the room. NULL for everything today.
ALTER TABLE comment ADD COLUMN room_id BIGINT NULL;
ALTER TABLE post_highlight ADD COLUMN room_id BIGINT NULL;

-- A member asked for their room posts to go too, alongside room_note_erasure.
CREATE TABLE room_post_erasure (
  user_id      BIGINT      NOT NULL,
  room_id      BIGINT      NOT NULL,
  requested_at DATETIME(6) NOT NULL,
  PRIMARY KEY (user_id, room_id),
  KEY idx_room_post_erasure_requested (requested_at),
  CONSTRAINT fk_room_post_erasure_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
