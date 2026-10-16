-- The domain in an account's handle when it is not the host that serves the account
-- (alice@example.com served by social.example.com). It is set only after the serving host's own
-- WebFinger names that handle, so no other server can claim the account, and the account is then
-- found by either domain. The lookup reads the username range of the existing (username, domain)
-- key, so no key of its own.
ALTER TABLE federation_remote_actor
  ADD COLUMN account_domain VARCHAR(255) NULL;
