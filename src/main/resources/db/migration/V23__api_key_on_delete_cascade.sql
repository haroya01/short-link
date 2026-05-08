ALTER TABLE api_key DROP FOREIGN KEY fk_api_key_user;
ALTER TABLE api_key
  ADD CONSTRAINT fk_api_key_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
