-- A closed room of up to 50 members. owner_id moves to another member when the owner leaves, so it
-- carries no foreign key; a room is never deleted, so its notes keep pointing at it.
CREATE TABLE room (
  id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id          BIGINT       NOT NULL,
  name              VARCHAR(50)  NOT NULL,
  approval_required BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at        DATETIME(6)  NOT NULL,
  updated_at        DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Membership is what NoteAccessSql.readable asks of a room note. last_read_note_id is one watermark
-- per member, moved by reading the room or its notes in a feed alike, and never moved back.
CREATE TABLE room_member (
  room_id           BIGINT      NOT NULL,
  user_id           BIGINT      NOT NULL,
  joined_at         DATETIME(6) NOT NULL,
  hidden_from_feed  BOOLEAN     NOT NULL DEFAULT FALSE,
  last_read_note_id BIGINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (room_id, user_id),
  KEY idx_room_member_user (user_id, room_id),
  CONSTRAINT fk_room_member_room FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE CASCADE,
  CONSTRAINT fk_room_member_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Any member shares one. short_code is the kurl link to the invite page; NULL when issuing it
-- failed, and the code alone still works. uses counts joins and requests, never past max_uses.
CREATE TABLE room_invite (
  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  room_id     BIGINT       NOT NULL,
  creator_id  BIGINT       NOT NULL,
  code        VARCHAR(16)  NOT NULL,
  short_code  VARCHAR(16)  NULL,
  max_uses    INT          NULL,
  uses        INT          NOT NULL DEFAULT 0,
  approval    BOOLEAN      NOT NULL DEFAULT FALSE,
  expires_at  DATETIME(6)  NULL,
  disabled_at DATETIME(6)  NULL,
  created_at  DATETIME(6)  NOT NULL,
  CONSTRAINT uk_room_invite_code UNIQUE (code),
  KEY idx_room_invite_room (room_id, id),
  CONSTRAINT fk_room_invite_room FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A join that waits for the owner, through an approval invite or a room that asks for approval.
CREATE TABLE room_join_request (
  room_id    BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  invite_id  BIGINT      NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (room_id, user_id),
  CONSTRAINT fk_room_join_request_room FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE CASCADE,
  CONSTRAINT fk_room_join_request_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Someone the owner removed: no invite lets them back in.
CREATE TABLE room_removal (
  room_id    BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (room_id, user_id),
  CONSTRAINT fk_room_removal_room FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE CASCADE,
  CONSTRAINT fk_room_removal_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A mutual follow the member said no to in "make a room with": never suggested again.
CREATE TABLE room_suggestion_dismissal (
  user_id    BIGINT      NOT NULL,
  friend_id  BIGINT      NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (user_id, friend_id),
  CONSTRAINT fk_room_suggestion_dismissal_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A room note's room. No foreign key: adding one would rebuild the note table.
ALTER TABLE note
  ADD COLUMN room_id BIGINT NULL,
  ADD KEY idx_note_room (room_id, id);
