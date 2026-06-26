package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.common.user.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * Purges the post slice's user-owned rows on account hard delete. Native SQL keeps the user slice
 * free of post entity types (see {@code common.user.UserDataEraser}). Order matters only for
 * post_block/post_revision → posts (their FKs lack ON DELETE CASCADE); comment/post_like/
 * post_bookmark carry no FK to posts, and post_tag/post_view_event cascade from posts.
 */
@Repository
class PostUserDataEraser implements UserDataEraser {

  @PersistenceContext private EntityManager em;

  @Override
  public void eraseFor(long userId) {
    // Likes this user placed on other authors' posts back a denormalized counter — settle it
    // before the like rows disappear, or those posts overcount forever.
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
    // comment_like / note_like carry a user FK WITHOUT ON DELETE CASCADE (V88/V89) — the likes this
    // user placed on *other* people's comments/notes would otherwise trip the final users delete
    // and
    // loop the cleanup job forever. Likes on this user's own comments cascade via the comment
    // delete
    // above; likes on their own notes cascade via note → users (note carries the cascade, note_like
    // → note does too), so only the user_id-scoped rows need an explicit purge here.
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
    // Highlight replies this user wrote on surviving highlights — replies under highlights we're
    // about to drop cascade via the highlight FK, but the user's own replies elsewhere must go
    // first or the final users delete trips fk_highlight_reply_user.
    execute("DELETE FROM highlight_reply WHERE user_id = :userId", userId);
    // Reader highlights and reading history are private per-user records (PII) — drop the ones this
    // user made, plus any others left on this user's own posts.
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
