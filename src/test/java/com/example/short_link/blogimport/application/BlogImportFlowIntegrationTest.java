package com.example.short_link.blogimport.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.blogimport.application.read.ImportSourceView;
import com.example.short_link.blogimport.application.write.BlogImportRunner;
import com.example.short_link.blogimport.application.write.ConnectImportSourceUseCase;
import com.example.short_link.blogimport.application.write.DisconnectImportSourceUseCase;
import com.example.short_link.blogimport.application.write.VerifyImportSourceUseCase;
import com.example.short_link.blogimport.domain.FeedValidators;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import com.example.short_link.post.application.read.PublicPostDetail;
import com.example.short_link.post.application.read.PublicPostQueryService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Commits for real: the import runs in its own transactions and on its own executor.
@SpringBootTest
@ActiveProfiles("test")
class BlogImportFlowIntegrationTest {

  private static final String FEED = "https://brunch.co.kr/rss/@@2xr";

  @MockitoBean private BlogFeedFetcher fetcher;
  @Autowired private ConnectImportSourceUseCase connect;
  @Autowired private VerifyImportSourceUseCase verify;
  @Autowired private DisconnectImportSourceUseCase disconnect;
  @Autowired private BlogImportRunner runner;
  @Autowired private PublicPostQueryService publicPosts;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private BlogImportProperties properties;

  private Long userId;
  private String username;

  @AfterEach
  void removeWhatTheFlowCommitted() {
    if (userId == null) {
      return;
    }
    jdbc.update("DELETE FROM blog_import_source WHERE user_id = ?", userId);
    jdbc.update(
        "DELETE FROM post_block WHERE post_id IN (SELECT id FROM posts WHERE user_id = ?)", userId);
    jdbc.update(
        "DELETE FROM post_revision WHERE post_id IN (SELECT id FROM posts WHERE user_id = ?)",
        userId);
    jdbc.update(
        "DELETE FROM post_search_text WHERE post_id IN (SELECT id FROM posts WHERE user_id = ?)",
        userId);
    jdbc.update("DELETE FROM posts WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM author_post_number WHERE user_id = ?", userId);
    jdbc.update(
        "DELETE FROM link_og_metadata WHERE link_id IN (SELECT id FROM link WHERE user_id = ?)",
        userId);
    jdbc.update(
        "DELETE FROM link_access_control WHERE link_id IN (SELECT id FROM link WHERE user_id = ?)",
        userId);
    jdbc.update(
        "DELETE FROM link_profile_binding WHERE link_id IN (SELECT id FROM link WHERE user_id = ?)",
        userId);
    jdbc.update(
        "DELETE FROM link_expiration_policy WHERE link_id IN (SELECT id FROM link WHERE user_id = ?)",
        userId);
    jdbc.update("DELETE FROM link WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  private void serve(String body) {
    when(fetcher.fetch(eq(FEED), any()))
        .thenReturn(new BlogFeedFetcher.Fetched(body, FeedValidators.NONE, false));
  }

  private void awaitStatus(Long sourceId, String status) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      String current =
          jdbc.queryForObject(
              "SELECT status FROM blog_import_source WHERE id = ?", String.class, sourceId);
      if (status.equals(current)) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("source " + sourceId + " never reached " + status);
  }

  private void signUp() {
    username = "bf" + UUID.randomUUID().toString().substring(0, 8);
    UserEntity user = new UserEntity(username + "@x.com", "google", "g-" + username);
    user.claimUsername(username);
    userId = users.save(user).getId();
  }

  @Test
  void naverStaysOutWhileItsRobotsTxtShutsEveryoneOut() {
    signUp();
    when(fetcher.robots("https://rss.blog.naver.com"))
        .thenReturn(Optional.of("User-agent: *\nDisallow: /\n\nUser-agent: Yeti\nDisallow: /\n"));

    assertThat(properties.robotsPolicy()).isEqualTo(BlogImportProperties.RobotsPolicy.RESPECT);
    assertThatThrownBy(() -> connect.execute(userId, "https://blog.naver.com/naverofficial"))
        .isInstanceOfSatisfying(
            BlogImportException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BlogImportErrorCode.IMPORT_ROBOTS_DISALLOWED));
    verify(fetcher, never()).fetch(any(), any());
  }

  @Test
  void aBrunchBlogComesInAsSummariesThatPointBackToTheOriginal() throws Exception {
    signUp();
    String brunch = Files.readString(Path.of("src/test/resources/blog-import/brunch.xml"));
    when(fetcher.robots(anyString())).thenReturn(Optional.empty());
    serve(brunch);

    ImportSourceView pending = connect.execute(userId, "https://brunch.co.kr/@@2xr");
    assertThat(pending.fullContent()).isFalse();
    String verified =
        brunch.replaceFirst(
            "<description>글이 작품이", "<description>" + pending.verificationCode() + " 글이 작품이");
    serve(verified);

    ImportSourceView importing = verify.execute(userId, pending.id());
    assertThat(importing.status()).isEqualTo("IMPORTING");
    awaitStatus(pending.id(), "SYNCING");

    List<String> slugs =
        jdbc.queryForList(
            "SELECT slug FROM posts WHERE import_source_id = ? AND status = 'PUBLISHED'"
                + " ORDER BY published_at",
            String.class,
            pending.id());
    assertThat(slugs).containsExactly("1", "2", "3");
    PublicPostDetail detail = publicPosts.findPublicPost(username, "3", null);
    assertThat(detail.post().title()).startsWith("브런치 독서클럽 떼독 오픈 안내");
    assertThat(detail.post().source().platform()).isEqualTo("BRUNCH");
    assertThat(detail.post().source().originalUrl()).isEqualTo("https://brunch.co.kr/@@2xr/432");
    assertThat(detail.post().source().excerptOnly()).isTrue();
    assertThat(detail.post().source().shortUrl()).isNotBlank();
    assertThat(detail.post().thumbnailUrl()).startsWith("https://img1.kakaocdn.net/");
    assertThat(detail.post().publishedAt())
        .isEqualTo(java.time.Instant.parse("2026-09-30T05:16:48Z"));
    assertThat(detail.blocks()).hasSize(1);
    assertThat(detail.blocks().get(0).content()).startsWith("읽고 싶은 책은");

    serve(
        verified.replaceFirst(
            "<item>",
            "<item><title>새 글</title><link>https://brunch.co.kr/@@2xr/433</link>"
                + "<description>요약입니다</description></item><item>"));
    CountDownLatch start = new CountDownLatch(1);
    CompletableFuture<Void> first =
        CompletableFuture.runAsync(
            () -> {
              await(start);
              runner.run(pending.id());
            });
    CompletableFuture<Void> second =
        CompletableFuture.runAsync(
            () -> {
              await(start);
              runner.run(pending.id());
            });
    start.countDown();
    CompletableFuture.allOf(first, second).get();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE import_source_id = ?", Long.class, pending.id()))
        .isEqualTo(4);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM blog_import_item WHERE source_id = ? AND imported_at IS NOT NULL",
                Long.class,
                pending.id()))
        .isEqualTo(4);

    disconnect.execute(userId, pending.id(), false);
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE user_id = ?", Long.class, userId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM blog_import_item WHERE source_id = ?",
                Long.class,
                pending.id()))
        .isZero();
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
