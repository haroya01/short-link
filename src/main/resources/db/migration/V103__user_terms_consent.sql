-- Click-wrap consent captured at sign-up (login/sign-up screen): which Terms/Privacy version the
-- user accepted and when, as proof of acceptance. NULL for accounts created before this column
-- existed — they accepted under the prior browse-wrap notice.
ALTER TABLE users
    ADD COLUMN terms_agreed_at DATETIME(6) NULL,
    ADD COLUMN terms_version   VARCHAR(32) NULL;
