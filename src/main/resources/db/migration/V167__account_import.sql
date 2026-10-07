CREATE TABLE account_import (
  id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT      NOT NULL,
  kind            VARCHAR(16) NOT NULL,
  total_items     INT         NOT NULL,
  processed_items INT         NOT NULL DEFAULT 0,
  imported_items  INT         NOT NULL DEFAULT 0,
  created_at      DATETIME(6) NOT NULL,
  finished_at     DATETIME(6) NULL,
  KEY idx_account_import_user (user_id, id),
  CONSTRAINT fk_account_import_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account_import_row (
  id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  import_id    BIGINT        NOT NULL,
  fields       VARCHAR(2048) NOT NULL,
  processed_at DATETIME(6)   NULL,
  KEY idx_account_import_row_pending (processed_at, id),
  CONSTRAINT fk_account_import_row_import
    FOREIGN KEY (import_id) REFERENCES account_import(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
