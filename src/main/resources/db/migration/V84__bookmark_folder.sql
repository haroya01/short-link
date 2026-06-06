-- Bookmark folders ("스마트 셸프"): optional user-made groupings over their reading list. A bookmark
-- with folder_id = NULL is unfiled (auto-grouped by tag in the UI). Folder names are unique per user.
CREATE TABLE bookmark_folder (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_bookmark_folder_user_name (user_id, name),
    KEY idx_bookmark_folder_user_created (user_id, created_at),
    CONSTRAINT fk_bookmark_folder_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- File bookmarks under a folder. Deleting a folder unfiles its bookmarks (folder_id -> NULL) rather
-- than removing them: the reading list survives, only its grouping is dropped.
ALTER TABLE post_bookmark
    ADD COLUMN folder_id BIGINT NULL AFTER user_id,
    ADD KEY idx_post_bookmark_folder (folder_id),
    ADD CONSTRAINT fk_post_bookmark_folder FOREIGN KEY (folder_id)
        REFERENCES bookmark_folder(id) ON DELETE SET NULL;
