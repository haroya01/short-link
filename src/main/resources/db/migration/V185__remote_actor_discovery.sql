-- Whether an account elsewhere lets itself into suggestions (toot:discoverable) and its public notes
-- into full-text search (toot:indexable), as its actor document says. Both start false and are
-- filled in when the account is next fetched (a cached account is fetched again once it is a day
-- old and in use), so nothing is shown before its server has said yes. The two keys let the account
-- search's prefixes (username, username@domain, display name) read ranges, not the whole table.
ALTER TABLE federation_remote_actor
  ADD COLUMN discoverable BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN indexable BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE federation_remote_actor
  ADD KEY idx_federation_remote_actor_username (username, domain),
  ADD KEY idx_federation_remote_actor_display_name (display_name);
