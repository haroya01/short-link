CREATE TABLE federation_domain_block (
  id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  domain     VARCHAR(255) NOT NULL,
  severity   VARCHAR(16)  NOT NULL,
  reason     VARCHAR(500) NULL,
  created_at DATETIME(6)  NOT NULL,
  CONSTRAINT uk_federation_domain_block UNIQUE (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
