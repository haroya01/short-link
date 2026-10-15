-- A note from another server keeps what it was there (NOTE, QUESTION, ARTICLE, PAGE or VIDEO; NULL
-- on rows kept before this is a NOTE), the title an article, page or video carries and the
-- community a page was posted to; its address stays in remote_url. A poll from elsewhere keeps its
-- server's counts, one per option in option order, comma separated, and its voters, since votes
-- are cast and counted there. Members' notes leave all five NULL.
ALTER TABLE note
  ADD COLUMN remote_kind      VARCHAR(16)  NULL,
  ADD COLUMN remote_title     VARCHAR(255) NULL,
  ADD COLUMN remote_community VARCHAR(255) NULL,
  ADD COLUMN poll_votes       VARCHAR(64)  NULL,
  ADD COLUMN poll_voters      INT          NULL;

-- When this server last looked at an account's outbox for notes it was never sent; the account
-- is looked at again only after six hours. NULL until the first look.
ALTER TABLE federation_remote_actor
  ADD COLUMN outbox_checked_at DATETIME(6) NULL;
