-- share_channels was CSV of channel names (just enabled-or-not). Replacing with a JSON column
-- that also carries each channel's user URL — visitors click a button and land on the user's
-- own X / LINE / Threads / Facebook / KakaoTalk account (Linktree-style social row).
ALTER TABLE users DROP COLUMN share_channels;
ALTER TABLE users ADD COLUMN socials VARCHAR(1024) NULL;
