package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Each concrete class runs a complete journey against its own disposable MySQL and Redis. */
abstract class ContentHttpJourneySupport extends DockerHttpTest {
  @LocalServerPort private int port;
  @Autowired private UserRepository users;
  @Autowired private JwtTokenService jwt;
  @Autowired private QueryInterceptor interceptor;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected JsonMapper json;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final List<CapturedRequest<?>> requests = new ArrayList<>();
  private HttpQueryContracts contracts;
  protected Actor author;
  protected Actor reader;
  protected Actor outsider;

  @BeforeEach
  void commitThreePeopleBeforeTheHttpJourney() {
    contracts = new HttpQueryContracts(interceptor, this::awaitAsyncWork);
    author = person("journey-author");
    reader = person("journey-reader");
    outsider = person("journey-outsider");
  }

  private Actor person(String username) {
    long id =
        new TransactionTemplate(transactionManager)
            .execute(
                transaction -> {
                  UserEntity user = new UserEntity(username + "@example.com", "google", username);
                  user.claimUsername(username);
                  return users.save(user).getId();
                });
    return new Actor(id, username, jwt.createAccessToken(id, "USER"));
  }

  protected JsonNode step(
      String id, String method, String path, Actor actor, Object body, int status)
      throws Exception {
    String response = rawStep(id, method, path, actor, body, status);
    return response.isBlank() ? json.nullNode() : json.readTree(response);
  }

  protected String rawStep(
      String id, String method, String path, Actor actor, Object body, int status)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(30))
            .header("X-Query-Contract-ID", id);
    if (actor != null) request.header("Authorization", "Bearer " + actor.token());
    HttpRequest.BodyPublisher payload = HttpRequest.BodyPublishers.noBody();
    if (body != null) {
      request.header("Content-Type", "application/json");
      payload = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
    }
    HttpRequest built = request.method(method, payload).build();
    var captured =
        contracts.capture(id, () -> http.send(built, HttpResponse.BodyHandlers.ofString()));
    requests.add(captured);
    assertThat(captured.response().statusCode())
        .as("%s: %s %s response=%s", id, method, path, captured.response().body())
        .isEqualTo(status);
    return captured.response().body();
  }

  protected byte[] download(String id, String path, Actor actor) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(30))
            .header("X-Query-Contract-ID", id)
            .header("Authorization", "Bearer " + actor.token())
            .GET()
            .build();
    var captured =
        contracts.capture(id, () -> http.send(request, HttpResponse.BodyHandlers.ofByteArray()));
    requests.add(captured);
    assertThat(captured.response().statusCode()).as("Download %s", path).isEqualTo(200);
    return captured.response().body();
  }

  protected JsonNode get(String id, String path, Actor actor) throws Exception {
    return step(id, "GET", path, actor, null, 200);
  }

  protected long createPost(String id, String slug, String title) throws Exception {
    long postId =
        step(
                id,
                "POST",
                "/api/v1/posts",
                author,
                Map.of("slug", slug, "title", title, "languageTag", "ko"),
                201)
            .path("id")
            .asLong();
    assertThat(postId).isPositive();
    assertThat(jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, postId))
        .isEqualTo(title);
    return postId;
  }

  protected void publish(String id, long postId) throws Exception {
    assertThat(
            step(id, "POST", postPath(postId) + "/publish", author, null, 200)
                .path("status")
                .asText())
        .isEqualTo("PUBLISHED");
    assertThat(jdbc.queryForObject("SELECT status FROM posts WHERE id = ?", String.class, postId))
        .isEqualTo("PUBLISHED");
  }

  /** Captures a real worker invocation separately from the HTTP requests that prepared its work. */
  protected void background(String id, Runnable work) throws Exception {
    var captured =
        contracts.captureBackgroundDelivery(
            id,
            () -> {
              work.run();
              return null;
            });
    requests.add(captured);
    assertThat(captured.queries()).as("Background delivery %s reached MySQL", id).isNotEmpty();
  }

  protected void verifyContracts() {
    assertAll(
        "Every HTTP step keeps its reviewed SQL contract",
        requests.stream()
            .map(
                captured ->
                    (org.junit.jupiter.api.function.Executable) () -> contracts.verify(captured)));
  }

  protected long count(String table, String predicate, Object... args) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE " + predicate, Long.class, args);
  }

  protected static String postPath(long postId) {
    return "/api/v1/posts/" + postId;
  }

  protected String publicPostPath(String slug) {
    return "/api/v1/public/profiles/" + author.username() + "/posts/" + slug;
  }

  protected record Actor(long id, String username, String token) {}
}
