package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.scheduler.PublishScheduledPostsJob;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * A real scheduled worker publishes an HTTP-authored post and delivers its follower's notification.
 */
class ScheduledPublicationHttpQueryContractTest extends ContentHttpJourneySupport {

  @Autowired private PublishScheduledPostsJob publishScheduled;
  @Autowired private StringRedisTemplate redis;

  @Test
  void duePostBecomesPublicAndNotifiesItsFollowerExactlyOnce() throws Exception {
    step(
        "schedule-reader-follows-author",
        "PUT",
        "/api/v1/users/" + author.username() + "/follow",
        reader,
        null,
        200);
    assertThat(
            count("user_follow", "follower_id = ? AND following_id = ?", reader.id(), author.id()))
        .isEqualTo(1);
    long postId =
        createPost("schedule-author-creates-draft", "scheduled-story", "A story published on time");
    step(
        "schedule-author-writes-body",
        "PUT",
        postPath(postId) + "/markdown",
        author,
        Map.of("markdown", "The scheduled reader journey reaches a real database."),
        200);
    step(
        "schedule-author-selects-future-time",
        "POST",
        postPath(postId) + "/schedule",
        author,
        Map.of("scheduledAt", Instant.now().plusSeconds(3600).toString()),
        200);
    assertThat(status(postId)).isEqualTo("SCHEDULED");
    step(
        "schedule-reader-cannot-open-draft",
        "GET",
        publicPostPath("scheduled-story"),
        null,
        null,
        404);

    background("schedule-worker-before-due-time", publishScheduled::tick);
    assertThat(status(postId)).isEqualTo("SCHEDULED");
    assertThat(newPostNotifications()).isZero();
    assertThat(redis.hasKey("kurl:post:scheduled-publish")).isFalse();

    makeScheduledTimeDue(postId);
    background("schedule-worker-publishes-due-post", publishScheduled::tick);
    assertPublishedState(postId);
    assertFollowerCanReadThePostAndItsNotification(postId);

    Timestamp firstPublication =
        jdbc.queryForObject("SELECT published_at FROM posts WHERE id = ?", Timestamp.class, postId);
    background("schedule-worker-repeated-tick", publishScheduled::tick);
    assertThat(newPostNotifications()).isEqualTo(1);
    assertThat(count("post_revision", "post_id = ?", postId)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT published_at FROM posts WHERE id = ?", Timestamp.class, postId))
        .isEqualTo(firstPublication);
    assertThat(redis.hasKey("kurl:post:scheduled-publish")).isFalse();
    verifyContracts();
  }

  private void makeScheduledTimeDue(long postId) {
    // Temporal fixture setup only: keep the HTTP-created post and move its due time into the past
    // without
    // sleeping an hour; the fixed date is past in every database/session time zone. This SQL is
    // outside the worker's query-capture window.
    assertThat(
            jdbc.update(
                "UPDATE posts SET scheduled_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),
                postId))
        .isEqualTo(1);
  }

  private void assertPublishedState(long postId) {
    assertThat(status(postId)).isEqualTo("PUBLISHED");
    assertThat(
            jdbc.queryForObject(
                "SELECT scheduled_at FROM posts WHERE id = ?", Timestamp.class, postId))
        .isNull();
    assertThat(count("post_revision", "post_id = ?", postId)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT search_text FROM post_search_text WHERE post_id = ?", String.class, postId))
        .contains("A story published on time", "real database");
    assertThat(newPostNotifications()).isEqualTo(1);
    assertThat(count("notification", "recipient_user_id = ? AND type = 'NEW_POST'", outsider.id()))
        .isZero();
  }

  private void assertFollowerCanReadThePostAndItsNotification(long postId) throws Exception {
    var detail =
        get("schedule-reader-opens-published-post", publicPostPath("scheduled-story"), null);
    assertThat(detail.path("post").path("id").asLong()).isEqualTo(postId);
    assertThat(detail.path("blocks").toString()).contains("real database");
    var notifications =
        get("schedule-follower-receives-new-post", "/api/v1/notifications", reader).path("items");
    assertThat(notifications.size()).isEqualTo(1);
    var notification = notifications.get(0);
    assertThat(notification.path("type").asText()).isEqualTo("NEW_POST");
    assertThat(notification.path("postId").asLong()).isEqualTo(postId);
    assertThat(notification.path("postSlug").asText()).isEqualTo("scheduled-story");
    assertThat(notification.path("actorUsername").asText()).isEqualTo(author.username());
    long notificationId = notification.path("id").asLong();
    assertThat(
            count(
                "notification",
                "id = ? AND recipient_user_id = ? AND actor_user_id = ?",
                notificationId,
                reader.id(),
                author.id()))
        .isEqualTo(1);
    step(
        "schedule-follower-marks-notification-read",
        "POST",
        "/api/v1/notifications/" + notificationId + "/read",
        reader,
        null,
        204);
    assertThat(count("notification", "id = ? AND read_at IS NOT NULL", notificationId))
        .isEqualTo(1);
  }

  private String status(long postId) {
    return jdbc.queryForObject("SELECT status FROM posts WHERE id = ?", String.class, postId);
  }

  private long newPostNotifications() {
    return count(
        "notification",
        "recipient_user_id = ? AND actor_user_id = ? AND type = 'NEW_POST'",
        reader.id(),
        author.id());
  }
}
