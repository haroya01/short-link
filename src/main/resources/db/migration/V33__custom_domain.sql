-- Custom domains let users serve their own short URLs (e.g. go.example.com/abc) while the
-- backend keeps doing the redirect resolution. Each domain belongs to one user. Verified flag
-- gates whether we honour the Host header in production traffic; an unverified domain is just
-- a pending claim and won't route anything.
CREATE TABLE custom_domain (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  domain VARCHAR(255) NOT NULL,
  verification_token VARCHAR(64) NOT NULL,
  verified BOOLEAN NOT NULL DEFAULT FALSE,
  verified_at DATETIME(3) NULL,
  last_checked_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_custom_domain_domain (domain),
  KEY idx_custom_domain_user (user_id),
  CONSTRAINT fk_custom_domain_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
