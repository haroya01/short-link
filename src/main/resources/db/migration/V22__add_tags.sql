CREATE TABLE tag (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  color CHAR(7),
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_tag_user_name (user_id, name),
  KEY idx_tag_user (user_id),
  CONSTRAINT fk_tag_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE link_tag (
  link_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (link_id, tag_id),
  KEY idx_link_tag_tag (tag_id),
  CONSTRAINT fk_link_tag_link FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE,
  CONSTRAINT fk_link_tag_tag FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
);
