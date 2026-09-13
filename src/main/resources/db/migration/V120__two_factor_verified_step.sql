-- Separate successful authentication from enrollment and recovery-code use.
ALTER TABLE user_two_factor ADD COLUMN last_verified_step BIGINT NULL;
