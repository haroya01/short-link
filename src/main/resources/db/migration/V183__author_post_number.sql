-- Per-author post numbers (kurl.me/@user/12). A post whose slug the client made up gets its author's
-- next number when it first leaves DRAFT. The counter only moves up, so a deleted post's number and
-- its shared links are never handed to another post.
CREATE TABLE author_post_number (
    user_id     BIGINT NOT NULL PRIMARY KEY,
    last_number INT    NOT NULL,
    CONSTRAINT fk_author_post_number_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
