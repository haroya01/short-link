ALTER TABLE link
  ADD COLUMN claim_token CHAR(32) NULL,
  ADD UNIQUE KEY uk_link_claim_token (claim_token);
