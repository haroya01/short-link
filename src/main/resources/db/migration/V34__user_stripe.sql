ALTER TABLE users
  ADD COLUMN stripe_customer_id VARCHAR(64) NULL,
  ADD COLUMN stripe_subscription_id VARCHAR(64) NULL,
  ADD COLUMN subscription_status VARCHAR(32) NULL,
  ADD COLUMN subscription_current_period_end DATETIME(6) NULL;

CREATE UNIQUE INDEX idx_users_stripe_customer ON users (stripe_customer_id);
