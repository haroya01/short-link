-- A note kept because a relay announced it while the relay setting was STORE (federation_relay_setting
-- now takes OFF, COUNT or STORE). Seven days after received_at the note is deleted, and this row with
-- it, unless a member liked, reposted, bookmarked, answered, quoted or voted on it, or follows its
-- author. relay_id is NULL once that relay is unsubscribed.
CREATE TABLE federation_relayed_note (
  note_id     BIGINT       NOT NULL PRIMARY KEY,
  relay_id    BIGINT       NULL,
  received_at DATETIME(6)  NOT NULL,
  KEY idx_federation_relayed_note_received (received_at),
  CONSTRAINT fk_federation_relayed_note_note
    FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE,
  CONSTRAINT fk_federation_relayed_note_relay
    FOREIGN KEY (relay_id) REFERENCES federation_relay(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- When an admin, or the relay stopping itself, last chose the mode. While NULL the mode named by
-- FEDERATION_RELAY_MODE is taken at start; once set, the row's own mode is kept.
ALTER TABLE federation_relay_setting
  ADD COLUMN chosen_at DATETIME(6) NULL;
