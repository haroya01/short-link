package com.example.short_link.blogimport.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.blogimport.application.read.ImportSourceView;
import com.example.short_link.blogimport.application.write.BlogImportRunner;
import com.example.short_link.blogimport.application.write.ConnectImportSourceUseCase;
import com.example.short_link.blogimport.application.write.DisconnectImportSourceUseCase;
import com.example.short_link.blogimport.application.write.VerifyImportSourceUseCase;
import com.example.short_link.post.application.read.PublicPostDetail;
import com.example.short_link.post.application.read.PublicPostQueryService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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

  private static final String FEED = "https://rss.blog.naver.com/naverofficial.xml";

  @MockitoBean private BlogFeedFetcher fetcher;
  @Autowired private ConnectImportSourceUseCase connect;
  @Autowired private VerifyImportSourceUseCase verify;
  @Autowired private DisconnectImportSourceUseCase disconnect;
  @Autowired private BlogImportRunner runner;
  @Autowired private PublicPostQueryService publicPosts;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

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

  @Test
  void aNaverBlogComesInAsSummariesThatPointBackToTheOriginal() throws Exception {
    username = "nv" + UUID.randomUUID().toString().substring(0, 8);
    UserEntity user = new UserEntity(username + "@x.com", "google", "g-" + username);
    user.claimUsername(username);
    userId = users.save(user).getId();
    String naver = Files.readString(Path.of("src/test/resources/blog-import/naver.xml"));
    when(fetcher.fetch(FEED)).thenReturn(naver);

    ImportSourceView pending = connect.execute(userId, "https://blog.naver.com/naverofficial");
    assertThat(pending.fullContent()).isFalse();
    when(fetcher.fetch(FEED))
        .thenReturn(
            naver.replace(
                "<description><![CDATA[차곡차곡",
                "<description><![CDATA[" + pending.verificationCode() + " 차곡차곡"));

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
    assertThat(detail.post().title()).isEqualTo("월 최대 40만 원! 라운지 서포터즈 참여하세요");
    assertThat(detail.post().source().platform()).isEqualTo("NAVER");
    assertThat(detail.post().source().originalUrl())
        .isEqualTo("https://blog.naver.com/naverofficial/224429356463");
    assertThat(detail.post().source().excerptOnly()).isTrue();
    assertThat(detail.post().source().shortUrl()).isNotBlank();
    assertThat(detail.post().thumbnailUrl()).startsWith("https://blogthumb.pstatic.net/");
    assertThat(detail.post().publishedAt())
        .isEqualTo(java.time.Instant.parse("2026-10-02T04:53:56Z"));
    assertThat(detail.blocks()).hasSize(1);
    assertThat(detail.blocks().get(0).content()).startsWith("실시간 관심사를").endsWith("…");

    String verified =
        naver.replace(
            "<description><![CDATA[차곡차곡",
            "<description><![CDATA[" + pending.verificationCode() + " 차곡차곡");
    when(fetcher.fetch(FEED))
        .thenReturn(
            verified.replaceFirst(
                "<item>",
                "<item><title>새 글</title>"
                    + "<link>https://blog.naver.com/naverofficial/9?fromRss=true</link>"
                    + "<guid>https://blog.naver.com/naverofficial/9</guid>"
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
