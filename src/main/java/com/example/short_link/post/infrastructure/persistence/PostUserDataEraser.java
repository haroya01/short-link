package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.common.user.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * Delete post_block/post_revision before posts: their FKs do not cascade. Comment/like/bookmark
 * references to posts need explicit cleanup; tags and view events cascade.
 */
@Repository
class PostUserDataEraser implements UserDataEraser {

  @PersistenceContext private EntityManager em;

  @Override
  public void eraseFor(long userId) {
    // Settle other authors' like counters before deleting this user's like rows.
    execute(
        """
        UPDATE posts p JOIN post_like pl ON pl.post_id = p.id
        SET p.like_count = GREATEST(p.like_count - 1, 0)
        WHERE pl.user_id = :userId AND p.user_id <> :userId
        """,
        userId);
    execute(
        "DELETE FROM comment WHERE user_id = :userId"
            + " OR post_id IN (SELECT id FROM posts WHERE user_id = :userId)",
        userId);
    // comment_like and note_like user FKs do not cascade; purge the user's likes on surviving
    // content.
    execute("DELETE FROM comment_like WHERE user_id = :userId", userId);
    execute("DELETE FROM note_like WHERE user_id = :userId", userId);
    execute(
        "DELETE FROM post_like WHERE user_id = :userId"
            + " OR post_id IN (SELECT id FROM posts WHERE user_id = :userId)",
        userId);
    execute(
        "DELETE FROM post_bookmark WHERE user_id = :userId"
            + " OR post_id IN (SELECT id FROM posts WHERE user_id = :userId)",
        userId);
    execute("DELETE FROM bookmark_folder WHERE user_id = :userId", userId);
    // Replies on surviving highlights must be removed before the user FK can be deleted.
    execute("DELETE FROM highlight_reply WHERE user_id = :userId", userId);
    execute(
        "DELETE FROM post_highlight WHERE user_id = :userId"
            + " OR post_id IN (SELECT id FROM posts WHERE user_id = :userId)",
        userId);
    execute(
        "DELETE FROM post_read WHERE user_id = :userId"
            + " OR post_id IN (SELECT id FROM posts WHERE user_id = :userId)",
        userId);
    execute(
        "DELETE FROM series_subscription WHERE user_id = :userId"
            + " OR series_id IN (SELECT id FROM series WHERE user_id = :userId)",
        userId);
    execute("DELETE FROM user_tag_pref WHERE user_id = :userId", userId);
    execute("DELETE FROM user_feed_pref WHERE user_id = :userId", userId);
    execute("DELETE FROM blog_webhook WHERE user_id = :userId", userId);
    execute(
        "DELETE FROM post_block WHERE post_id IN (SELECT id FROM posts WHERE user_id = :userId)",
        userId);
    execute(
        "DELETE FROM post_revision WHERE post_id IN (SELECT id FROM posts WHERE user_id = :userId)",
        userId);
    execute("DELETE FROM posts WHERE user_id = :userId", userId);
    execute("DELETE FROM series WHERE user_id = :userId", userId);
  }

  private void execute(String sql, long userId) {
    em.createNativeQuery(sql).setParameter("userId", userId).executeUpdate();
  }
}
