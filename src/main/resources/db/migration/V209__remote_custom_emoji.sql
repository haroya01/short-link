-- Custom emojis another server draws in place of :shortcode: in a note's text (remote_emojis) or
-- in an account's name (emojis), as [{"shortcode": "...", "url": "https://..."}]: at most 50,
-- shortcodes without colons and unique within a row. NULL means none: members' notes, accounts
-- without any, and rows kept before this. A note's list changes only when its server sends an
-- Update; an account's each time it is fetched again.
ALTER TABLE note
  ADD COLUMN remote_emojis JSON NULL;

ALTER TABLE federation_remote_actor
  ADD COLUMN emojis JSON NULL;
