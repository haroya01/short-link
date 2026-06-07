-- In-app notifications: one row per "someone interacted with you" event (like / comment / follow).
-- Written by an after-commit listener off BlogInteractionEvent, so a rolled-back interaction never
-- leaves a phantom notification. The actor's display identity is resolved at read time (a join on
-- users by actor_user_id) so names stay current and the write path stays a single insert; `payload`
-- snapshots only the point-in-time post reference (slug/title) that should survive a later edit.

CREATE TABLE notification (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id BIGINT       NOT NULL,
    type              VARCHAR(32)  NOT NULL,
    actor_user_id     BIGINT       NULL,
    payload           JSON         NULL,
    read_at           DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    -- (recipient, id) drives the newest-first cursor scan; (recipient, read_at) the unread count.
    KEY idx_notification_recipient (recipient_user_id, id),
    KEY idx_notification_unread (recipient_user_id, read_at),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
