-- Following a member turns on notices for their new notes. Follows made before this switch on once;
-- each one can still be turned off afterwards.
ALTER TABLE user_follow ALTER COLUMN notify_notes SET DEFAULT TRUE;
UPDATE user_follow SET notify_notes = TRUE WHERE notify_notes = FALSE;
