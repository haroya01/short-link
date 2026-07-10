-- Per-user opt-out that withholds follower / following totals from the public author page.
-- Default FALSE — counts stay public unless the owner turns them off from the profile edit
-- screen. When TRUE the follow-status response omits both counts entirely (not zeroed).
ALTER TABLE users
    ADD COLUMN hide_follower_count BOOLEAN NOT NULL DEFAULT FALSE;
