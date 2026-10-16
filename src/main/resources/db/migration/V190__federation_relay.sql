-- A relay this server subscribes to as its instance actor, such as one of FediBuzz's per-tag relays.
-- It stays PENDING until the relay answers the Follow, and only an ACCEPTED relay's announces are
-- read. Unsubscribing deletes the row, so its announces are ignored from that moment.
CREATE TABLE federation_relay (
  id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor_uri          VARCHAR(512)  NOT NULL,
  inbox              VARCHAR(512)  NOT NULL,
  label              VARCHAR(255)  NULL,
  follow_activity_id VARCHAR(600)  NOT NULL,
  state              VARCHAR(16)   NOT NULL,
  accepted_at        DATETIME(6)   NULL,
  created_at         DATETIME(6)   NOT NULL,
  updated_at         DATETIME(6)   NOT NULL,
  UNIQUE KEY uk_federation_relay_actor (actor_uri),
  UNIQUE KEY uk_federation_relay_follow (follow_activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One row: what is done with the relays' announces. OFF reads none. COUNT fetches each announced
-- post from its own server, checks it and counts the outcome, keeping nothing. A post is wanted
-- only in one of the listed languages.
CREATE TABLE federation_relay_setting (
  id         TINYINT       NOT NULL PRIMARY KEY,
  mode       VARCHAR(16)   NOT NULL,
  languages  VARCHAR(255)  NOT NULL,
  created_at DATETIME(6)   NOT NULL,
  updated_at DATETIME(6)   NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO federation_relay_setting (id, mode, languages, created_at, updated_at)
VALUES (1, 'COUNT', 'ko,ja', NOW(6), NOW(6));
