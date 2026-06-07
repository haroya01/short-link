-- Per-post preview token: lets the owner share a not-yet-public post via an unguessable link
-- ({slug}?preview={token}) without publishing it. The public read path bypasses the status guard
-- when the token matches. NULL until the owner first requests a link; unique so a token resolves to
-- exactly one post (MySQL permits multiple NULLs under a unique index, so unissued posts don't
-- collide).

ALTER TABLE posts ADD COLUMN preview_token VARCHAR(64) NULL;
ALTER TABLE posts ADD CONSTRAINT uk_posts_preview_token UNIQUE (preview_token);
