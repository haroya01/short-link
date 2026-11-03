-- A link-only (UNLISTED) collection is shown to someone other than its owner only when the request
-- carries unlisted_key (X-Collection-Key), 24 random bytes as unpadded url-safe base64, the same
-- shape as posts.unlisted_key. It is made on the first switch to UNLISTED and kept until the owner
-- rotates it. Every collection that is UNLISTED today gets a key here, so links shared before this
-- (by number only) stop opening for anyone but the owner. PRIVATE and PUBLIC rows stay NULL.
ALTER TABLE collection
  ADD COLUMN unlisted_key VARCHAR(32) NULL;

UPDATE collection
SET unlisted_key = REPLACE(REPLACE(TO_BASE64(RANDOM_BYTES(24)), '+', '-'), '/', '_')
WHERE visibility = 'UNLISTED';
