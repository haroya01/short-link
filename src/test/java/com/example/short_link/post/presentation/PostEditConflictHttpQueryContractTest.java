package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import com.example.short_link.testsupport.DockerHttpTest;
import com.example.short_link.testsupport.HttpQueryContracts;
import com.example.short_link.testsupport.HttpQueryContracts.CapturedRequest;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import io.queryaudit.core.interceptor.QueryInterceptor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PostEditConflictHttpQueryContractTest extends DockerHttpTest {

  private static final long OTHER_DEVICE_VERSION = 2L;

  @LocalServerPort private int port;
  @Autowired private PostRepository posts;
  @Autowired private PostBlockRepository blocks;
  @Autowired private PostRevisionRepository revisions;
  @Autowired private UserRepository users;
  @Autowired private JwtTokenService jwt;
  @Autowired private QueryInterceptor interceptor;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private TransactionTemplate transactions;
  private HttpQueryContracts contracts;
  private Long authorId;
  private String accessToken;
  private long postId;

  @BeforeEach
  void commitAPostTheOtherDeviceSavedTwice() {
    transactions = new TransactionTemplate(transactionManager);
    contracts = new HttpQueryContracts(interceptor);
    authorId =
        transactions.execute(
            transaction -> {
              UserEntity author =
                  new UserEntity("http-edit-conflict@example.com", "google", "http-edit-conflict");
              author.claimUsername("http-edit-conflict");
              return users.save(author).getId();
            });
    accessToken = jwt.createAccessToken(authorId, "USER");
    postId =
        transactions.execute(
            transaction -> {
              PostEntity post =
                  posts.save(new PostEntity(authorId, "edit-conflict", "Their title", "ko"));
              blocks.insertAll(
                  List.of(
                      new PostBlockEntity(post.getId(), PostBlockType.PARAGRAPH, "their body", 0)));
              return post.getId();
            });
    jdbc.update("UPDATE posts SET content_version = ? WHERE id = ?", OTHER_DEVICE_VERSION, postId);
  }

  @AfterEach
  void removeOnlyThisAuthorsCommittedData() {
    if (authorId == null) return;
    transactions.executeWithoutResult(
        transaction -> {
          for (PostEntity post : posts.findAllByUserIdOrderByCreatedAtDesc(authorId)) {
            revisions.deleteAllByPostId(post.getId());
            blocks.deleteAllByPostId(post.getId());
            jdbc.update("DELETE FROM post_search_text WHERE post_id = ?", post.getId());
            posts.delete(post);
          }
          posts.flush();
          users.deleteById(authorId);
        });
  }

  @Test
  void bodySavedFromTheCurrentVersionAnswersWithTheNextOne() throws Exception {
    var captured =
        sendJson(
            "post-edit-blocks-current-version",
            "PUT",
            postPath() + "/blocks",
            Map.of("blocks", List.of(paragraph("my body")), "baseVersion", OTHER_DEVICE_VERSION));

    assertStatus(captured, 200);
    assertThat(captured.response().headers().firstValue("X-Content-Version")).hasValue("3");
    assertThat(committedVersion()).isEqualTo(3L);
    assertThat(committedBody()).containsExactly("my body");
    contracts.verify(captured);
  }

  @Test
  void bodySavedFromAnOlderVersionIsRefusedWithTheCurrentOne() throws Exception {
    var captured =
        sendJson(
            "post-edit-blocks-stale-version",
            "PUT",
            postPath() + "/blocks",
            Map.of("blocks", List.of(paragraph("my body")), "baseVersion", 1));

    assertConflict(captured);
    assertThat(captured.counts().insertCount()).isZero();
    assertThat(captured.counts().updateCount()).isZero();
    assertThat(captured.counts().deleteCount()).isZero();
    assertThat(committedVersion()).isEqualTo(OTHER_DEVICE_VERSION);
    assertThat(committedBody()).containsExactly("their body");
    contracts.verify(captured);
  }

  @Test
  void metadataSavedFromAnOlderVersionIsRefusedWithTheCurrentOne() throws Exception {
    var captured =
        sendJson(
            "post-edit-metadata-stale-version",
            "PATCH",
            postPath(),
            Map.of("title", "My title", "baseVersion", 1));

    assertConflict(captured);
    assertThat(captured.counts().updateCount()).isZero();
    assertThat(committedTitle()).isEqualTo("Their title");
    assertThat(committedVersion()).isEqualTo(OTHER_DEVICE_VERSION);
    contracts.verify(captured);
  }

  @Test
  void metadataSavedFromTheCurrentVersionAnswersWithTheNextOne() throws Exception {
    var captured =
        sendJson(
            "post-edit-metadata-current-version",
            "PATCH",
            postPath(),
            Map.of("title", "My title", "baseVersion", OTHER_DEVICE_VERSION));

    assertStatus(captured, 200);
    JsonNode body = json.readTree(captured.response().body());
    assertThat(body.get("title").asText()).isEqualTo("My title");
    assertThat(body.get("contentVersion").asLong()).isEqualTo(3L);
    assertThat(committedVersion()).isEqualTo(3L);
    contracts.verify(captured);
  }

  @Test
  void overwriteKeepsTheReplacedBodyInHistory() throws Exception {
    var captured =
        sendJson(
            "post-edit-markdown-overwrite",
            "PUT",
            postPath() + "/markdown",
            Map.of("markdown", "my body", "baseVersion", 1, "overwrite", true));

    assertStatus(captured, 200);
    JsonNode body = json.readTree(captured.response().body());
    assertThat(body.get("markdown").asText()).isEqualTo("my body");
    assertThat(body.get("contentVersion").asLong()).isEqualTo(3L);
    assertThat(committedBody()).containsExactly("my body");
    var kept =
        jdbc.queryForMap(
            "SELECT version_number, title_snapshot, content_json FROM post_revision WHERE post_id = ?",
            postId);
    assertThat(kept.get("title_snapshot")).isEqualTo("Their title");
    assertThat((String) kept.get("content_json")).contains("their body");
    contracts.verify(captured);

    HttpResponse<String> reread =
        http.send(
            authorizedRequest(postPath() + "/markdown").GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(json.readTree(reread.body()).get("contentVersion").asLong()).isEqualTo(3L);

    HttpResponse<String> restored =
        http.send(
            authorizedRequest(postPath() + "/revisions/" + kept.get("version_number") + "/restore")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(restored.statusCode()).isEqualTo(200);
    assertThat(json.readTree(restored.body()).get("contentVersion").asLong()).isEqualTo(4L);
    assertThat(committedBody()).containsExactly("their body");
    assertThat(committedTitle()).isEqualTo("Their title");
  }

  private void assertConflict(CapturedRequest<HttpResponse<String>> captured) {
    assertStatus(captured, 409);
    JsonNode problem = json.readTree(captured.response().body());
    assertThat(problem.get("code").asText()).isEqualTo("POST_EDIT_CONFLICT");
    assertThat(problem.get("contentVersion").asLong()).isEqualTo(OTHER_DEVICE_VERSION);
  }

  private long committedVersion() {
    return jdbc.queryForObject(
        "SELECT content_version FROM posts WHERE id = ?", Long.class, postId);
  }

  private String committedTitle() {
    return jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, postId);
  }

  private List<String> committedBody() {
    return jdbc.queryForList(
        "SELECT content FROM post_block WHERE post_id = ? ORDER BY block_order",
        String.class,
        postId);
  }

  private static Map<String, String> paragraph(String content) {
    return Map.of("type", "PARAGRAPH", "content", content);
  }

  private CapturedRequest<HttpResponse<String>> sendJson(
      String contractId, String method, String path, Object body) throws Exception {
    HttpRequest request =
        authorizedRequest(path)
            .header("Content-Type", "application/json")
            .header("X-Query-Contract-ID", contractId)
            .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
    return contracts.capture(
        contractId, () -> http.send(request, HttpResponse.BodyHandlers.ofString()));
  }

  private HttpRequest.Builder authorizedRequest(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("Authorization", "Bearer " + accessToken)
        .timeout(Duration.ofSeconds(30));
  }

  private void assertStatus(CapturedRequest<HttpResponse<String>> captured, int expectedStatus) {
    assertThat(captured.response().statusCode())
        .as("HTTP response: %s", captured.response().body())
        .isEqualTo(expectedStatus);
    assertThat(captured.queries()).isNotEmpty();
  }

  private String postPath() {
    return "/api/v1/posts/" + postId;
  }
}
