package com.example.short_link.link.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.og.application.OgScraper;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real HTTP/JDBC journeys; only remote web-page metadata is supplied by a deterministic fixture.
 */
public abstract class LinkJourneyHttpSupport extends DockerHttpTest {
  @LocalServerPort protected int port;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected JsonMapper json;
  @Autowired private UserRepository users;
  @Autowired private JwtTokenService jwt;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private QueryInterceptor interceptor;
  @Autowired private CacheManager caches;
  @MockitoBean protected OgScraper remotePage;

  protected final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  protected Actor owner;
  protected Actor stranger;
  protected HttpQueryContracts contracts;
  protected final List<CapturedRequest<?>> captures = new ArrayList<>();

  @BeforeEach
  void prepareActorsAndRemoteMetadata() {
    contracts = new HttpQueryContracts(interceptor, this::awaitAsyncWork);
    captures.clear();
    // Each story starts cold, so exact SQL contracts do not depend on method execution order.
    caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
    owner = actor("owner");
    stranger = actor("stranger");
    when(remotePage.fetch(anyString()))
        .thenReturn(new OgMetadata("Fixture destination", "Remote metadata fixture", null));
  }

  @AfterEach
  void verifyEveryExecutedRequestContract() {
    awaitAsyncWrites();
    assertAll(captures.stream().map(captured -> () -> contracts.verify(captured)));
  }

  protected Actor actor(String role) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    long id =
        new TransactionTemplate(transactionManager)
            .execute(
                status -> {
                  UserEntity user =
                      new UserEntity(role + suffix + "@example.test", "google", role + suffix);
                  user.claimUsername(role + suffix);
                  return users.save(user).getId();
                });
    return new Actor(id, jwt.createAccessToken(id, "USER"));
  }

  protected JsonNode request(
      String id, Actor actor, String method, String path, Object body, int expectedStatus)
      throws Exception {
    HttpResponse<byte[]> response =
        raw(
            id,
            actor,
            method,
            path,
            body == null ? null : json.writeValueAsString(body),
            "application/json",
            expectedStatus);
    return response.body().length == 0 ? json.nullNode() : json.readTree(response.body());
  }

  protected HttpResponse<byte[]> raw(
      String id,
      Actor actor,
      String method,
      String path,
      String body,
      String contentType,
      int expectedStatus)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Mozilla/5.0 HTTP journey browser")
            .header("X-Query-Contract-ID", id);
    if (actor != null) request.header("Authorization", "Bearer " + actor.token());
    if (body != null) request.header("Content-Type", contentType);
    request.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    var captured =
        contracts.capture(
            id,
            () -> {
              HttpResponse<byte[]> result =
                  http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
              return result;
            });
    captures.add(captured);
    assertThat(captured.response().statusCode())
        .as("%s: %s", id, new String(captured.response().body(), StandardCharsets.UTF_8))
        .isEqualTo(expectedStatus);
    return captured.response();
  }

  protected String createLink(String id) throws Exception {
    String code = "H" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    JsonNode response =
        request(
            id,
            owner,
            "POST",
            "/api/v1/links",
            Map.of("url", "https://example.com/" + code, "customCode", code),
            201);
    assertThat(response.path("shortCode").asText()).isEqualTo(code);
    assertThat(
            number(
                "SELECT COUNT(*) FROM link WHERE short_code = ? AND user_id = ?", code, owner.id()))
        .isEqualTo(1);
    return code;
  }

  protected long linkId(String code) {
    return number("SELECT id FROM link WHERE short_code = ?", code);
  }

  protected long number(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  protected String text(String sql, Object... args) {
    return jdbc.queryForObject(sql, String.class, args);
  }

  protected void awaitAsyncWrites() {
    awaitAsyncWork();
  }

  protected record Actor(long id, String token) {}
}
