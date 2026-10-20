-- A member asked someone in by name. The invitee accepts or declines; a declined row stays so the
-- same room cannot ask again.
CREATE TABLE room_invitation (
  room_id     BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  inviter_id  BIGINT      NOT NULL,
  created_at  DATETIME(6) NOT NULL,
  declined_at DATETIME(6) NULL,
  PRIMARY KEY (room_id, user_id),
  KEY idx_room_invitation_user (user_id, created_at),
  CONSTRAINT fk_room_invitation_room FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE CASCADE,
  CONSTRAINT fk_room_invitation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_room_invitation_inviter FOREIGN KEY (inviter_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A request that came from an invitation rather than an invite link has no link to point at.
ALTER TABLE room_join_request MODIFY invite_id BIGINT NULL;
