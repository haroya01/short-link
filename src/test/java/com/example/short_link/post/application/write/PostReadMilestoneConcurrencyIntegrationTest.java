package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.analytics.Utm;
import com.example.short_link.common.analytics.VisitRequest;
import com.example.short_link.post.application.access.PostReader;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// Readers arrive at once; every threshold must still be crossed, recorded and announced once.
@SpringBootTest
@ActiveProfiles("test")
class PostReadMilestoneConcurrencyIntegrationTest {

  private static final String BROWSER =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/130.0.0.0 Safari/537.36";

  @Autowired private RecordPostViewUseCase views;
  @Autowired private UserRepository users;
  @Autowired private PostRepository posts;
  @Autowired private JdbcTemplate jdbc;

  private Long authorId;
  private String username;

  @BeforeEach
  void author() {
    username = "rd" + UUID.randomUUID().toString().substring(0, 8);
    UserEntity user = new UserEntity(username + "@x.com", "google", "g-" + username);
    user.claimUsername(username);
    authorId = users.save(user).getId();
  }

  @AfterEach
  void removeWhatTheTestCommitted() {
    jdbc.update("DELETE FROM notification WHERE recipient_user_id = ?", authorId);
    jdbc.update(
        "DELETE FROM post_view_event WHERE post_id IN (SELECT id FROM posts WHERE user_id = ?)",
        authorId);
    jdbc.update("DELETE FROM posts WHERE user_id = ?", authorId);
    jdbc.update("DELETE FROM users WHERE id = ?", authorId);
  }

  private PostEntity written(String slug) {
    PostEntity post = new PostEntity(authorId, slug, "읽힌 글", "ko");
    DiscoverableBodies.discoverable(post);
    post.publish();
    return posts.save(post);
  }

  private static VisitRequest visitor(int i, String userAgent) {
    return new VisitRequest(
        null,
        userAgent,
        "203.0.113." + i,
        "ko",
        null,
        new Utm(null, null, null, null, null),
        false,
        null);
  }

  private void runTogether(List<Runnable> tasks) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<?>> done = new ArrayList<>();
      for (Runnable task : tasks) {
        done.add(
            pool.submit(
                () -> {
                  start.await();
                  task.run();
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> future : done) {
        future.get();
      }
    } finally {
      pool.shutdown();
    }
  }

  private List<Integer> milestones(Long postId) {
    return jdbc.queryForList(
        "SELECT milestone FROM post_read_milestone WHERE post_id = ? ORDER BY milestone",
        Integer.class,
        postId);
  }

  private long notices(String type) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    long count = 0;
    while (System.nanoTime() < deadline) {
      count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM notification WHERE recipient_user_id = ? AND type = ?",
              Long.class,
              authorId,
              type);
      if (count > 0) {
        Thread.sleep(300);
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM notification WHERE recipient_user_id = ? AND type = ?",
            Long.class,
            authorId,
            type);
      }
      Thread.sleep(50);
    }
    return count;
  }

  @Test
  void twelveVisitorsArrivingTwiceAtOnceAreTwelveReadersAndTwoNotices() throws Exception {
    PostEntity post = written("together");
    List<Runnable> tasks = new ArrayList<>();
    for (int i = 1; i <= 12; i++) {
      int visitor = i;
      for (int twice = 0; twice < 2; twice++) {
        tasks.add(
            () ->
                views.execute(
                    new RecordPostViewCommand(username, "together"),
                    visitor(visitor, BROWSER),
                    PostReader.of(null)));
      }
    }

    runTogether(tasks);

    assertThat(
            jdbc.queryForObject(
                "SELECT human_reader_count FROM posts WHERE id = ?", Integer.class, post.getId()))
        .isEqualTo(12);
    assertThat(milestones(post.getId())).containsExactly(1, 10);
    assertThat(notices("POST_FIRST_READER")).isEqualTo(1);
    assertThat(notices("POST_READ_MILESTONE")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT payload FROM notification WHERE recipient_user_id = ?"
                    + " AND type = 'POST_READ_MILESTONE'",
                String.class,
                authorId))
        .containsPattern("\"milestone\":\\s*10")
        .containsPattern("\"title\":\\s*\"읽힌 글\"");
  }

  @Test
  void theAuthorABotAndAPrivacyRequestAreNotReaders() throws Exception {
    PostEntity post = written("alone");
    views.execute(
        new RecordPostViewCommand(username, "alone", authorId),
        visitor(50, BROWSER),
        PostReader.of(authorId));
    views.execute(
        new RecordPostViewCommand(username, "alone"),
        visitor(51, "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"),
        PostReader.of(null));
    views.execute(
        new RecordPostViewCommand(username, "alone"),
        new VisitRequest(
            null,
            BROWSER,
            "203.0.113.52",
            "ko",
            null,
            new Utm(null, null, null, null, null),
            true,
            null),
        PostReader.of(null));

    assertThat(
            jdbc.queryForObject(
                "SELECT human_reader_count FROM posts WHERE id = ?", Integer.class, post.getId()))
        .isZero();
    assertThat(milestones(post.getId())).isEmpty();
  }
}
