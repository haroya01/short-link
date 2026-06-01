-- The kurl short code a CTA's target is wrapped into, so clicks on the CTA flow through the redirect
-- and get attributed (post_id) and measured. Nullable (tracking is best-effort); not unique — two
-- CTAs pointing at the same URL dedupe to the same short link, hence the same code.
ALTER TABLE cta ADD COLUMN tracked_short_code VARCHAR(16) NULL;
