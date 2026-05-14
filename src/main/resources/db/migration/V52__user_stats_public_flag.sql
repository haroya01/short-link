-- Per-user toggle exposing public profile visit stats. Default FALSE because not every user
-- wants visit counts publicly visible (unlike short links where the public stats page is opt-out
-- by visibility=PUBLIC). Owner can flip it from /profile/edit; when true, anonymous visitors can
-- hit GET /api/v1/public/profiles/{username}/stats and the new /u/<username>/stats page renders.
ALTER TABLE users
    ADD COLUMN is_stats_public BOOLEAN NOT NULL DEFAULT FALSE;
