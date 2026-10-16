-- Who may read a published post: PUBLIC, UNLISTED (only with unlisted_key), FOLLOWERS or PASSWORD.
-- password_hash is bcrypt over base64(SHA-256(password)) and stays when the post leaves PASSWORD, so
-- switching back needs no new password. unlisted_key is made on the first switch to UNLISTED and
-- kept until the author rotates it. blocked_countries is the same upper-case ISO 3166-1 alpha-2 CSV
-- as link.blocked_countries, NULL when no country is blocked; it holds for every audience. Every
-- post that exists today stays PUBLIC with nothing blocked.
ALTER TABLE posts
  ADD COLUMN audience          VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC',
  ADD COLUMN password_hash     VARCHAR(60)  NULL,
  ADD COLUMN unlisted_key      VARCHAR(32)  NULL,
  ADD COLUMN blocked_countries VARCHAR(255) NULL;
