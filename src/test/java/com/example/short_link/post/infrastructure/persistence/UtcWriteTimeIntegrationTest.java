package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.infrastructure.persistence.JpaBlogNotificationPreferenceRepository;
import com.example.short_link.notification.infrastructure.persistence.JpaNotificationPreferenceRepository;
import com.example.short_link.post.domain.PostLikeEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UtcWriteTimeIntegrationTest {

  private static final long USER = 9_100_001L;
  private static final List<String[]> TABLES =
      List.of(
          new String[] {"post_like", "post_id"},
          new String[] {"comment_like", "comment_id"},
          new String[] {"highlight_reply_like", "highlight_reply_id"},
          new String[] {"post_bookmark", "post_id"},
          new String[] {"series_subscription", "series_id"});

  @Autowired private JdbcTemplate jdbc;
  @Autowired private JpaPostLikeRepository postLikes;
  @Autowired private JpaCommentLikeRepository commentLikes;
  @Autowired private JpaHighlightReplyLikeRepository replyLikes;
  @Autowired private JpaPostBookmarkRepository bookmarks;
  @Autowired private JpaSeriesSubscriptionRepository subscriptions;
  @Autowired private JpaNotificationPreferenceRepository linkPreferences;
  @Autowired private JpaBlogNotificationPreferenceRepository blogPreferences;
  @PersistenceContext private EntityManager em;

  @BeforeEach
  void withoutParentRows() {
    jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
  }

  @AfterEach
  void restoreChecks() {
    jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
  }

  @Test
  void theBackfillBringsSessionClockRowsBackToUtcAndLeavesJpaRowsAlone() throws IOException {
    Instant saved =
        Instant.ofEpochSecond(
            Instant.now().minus(Duration.ofMinutes(5)).getEpochSecond(), 123_456_000);
    for (String[] table : TABLES) {
      jdbc.update(
          "INSERT INTO "
              + table[0]
              + " ("
              + table[1]
              + ", user_id, created_at) VALUES (1, ?, NOW())",
          USER);
      em.createNativeQuery(
              "INSERT INTO "
                  + table[0]
                  + " ("
                  + table[1]
                  + ", user_id, created_at) VALUES (2, :user, :at)")
          .setParameter("user", USER)
          .setParameter("at", saved)
          .executeUpdate();
    }

    for (String statement : backfill()) {
      jdbc.update(statement);
    }

    for (String[] table : TABLES) {
      assertThat(secondsBehindUtc(table[0], table[1], 1)).as(table[0]).isBetween(-5L, 60L);
      assertThat(secondsBehindUtc(table[0], table[1], 2)).as(table[0]).isBetween(295L, 360L);
    }
    em.clear();
    assertThat(likeAt(1)).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(5));
    assertThat(likeAt(2)).isEqualTo(saved);
  }

  @Test
  void newRowsAreWrittenInUtc() {
    postLikes.insertIgnore(3L, USER);
    commentLikes.insertIgnore(3L, USER);
    replyLikes.insertIgnore(3L, USER);
    bookmarks.insertIgnore(3L, USER);
    subscriptions.insertIgnore(USER, 3L);
    linkPreferences.setEnabled(USER, LinkNotificationType.values()[0].name(), true);
    blogPreferences.setEnabled(USER, NotificationType.LIKE.name(), true);

    for (String[] table : TABLES) {
      assertThat(secondsBehindUtc(table[0], table[1], 3)).as(table[0]).isBetween(-5L, 60L);
    }
    for (String table : List.of("notification_preference", "blog_notification_preference")) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP(6)) FROM "
                      + table
                      + " WHERE user_id = ?",
                  Long.class,
                  USER))
          .as(table)
          .isBetween(-5L, 60L);
    }
    em.clear();
    assertThat(likeAt(3)).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(5));
  }

  private long secondsBehindUtc(String table, String subject, long subjectId) {
    return jdbc.queryForObject(
        "SELECT TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP(6)) FROM "
            + table
            + " WHERE "
            + subject
            + " = ? AND user_id = ?",
        Long.class,
        subjectId,
        USER);
  }

  private Instant likeAt(long postId) {
    return em.createQuery(
            "SELECT l FROM PostLikeEntity l WHERE l.postId = :post AND l.userId = :user",
            PostLikeEntity.class)
        .setParameter("post", postId)
        .setParameter("user", USER)
        .getSingleResult()
        .getCreatedAt();
  }

  private static List<String> backfill() throws IOException {
    String sql =
        new ClassPathResource("db/migration/V205__now_written_rows_to_utc.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    return Arrays.stream(sql.replaceAll("(?m)^--.*$", "").split(";"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }
}
