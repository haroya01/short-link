-- A device token belongs to the login session that registered it. Logging out deletes the session's
-- tokens, each refresh pushes session_expires_at forward, and pushes skip tokens whose session has
-- ended, so a session that lapses silently (refresh token expiry) stops receiving pushes too. Rows
-- registered before sessions existed keep NULL and are sent to until the app registers again.
ALTER TABLE device_token
    ADD COLUMN session_id         VARCHAR(36) NULL,
    ADD COLUMN session_expires_at DATETIME(6) NULL,
    ADD KEY idx_device_token_session (user_id, session_id);
