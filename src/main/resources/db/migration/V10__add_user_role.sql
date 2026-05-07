ALTER TABLE users
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER';

CREATE INDEX idx_users_role ON users (role);
