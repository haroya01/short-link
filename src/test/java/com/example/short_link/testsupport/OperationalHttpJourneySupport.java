package com.example.short_link.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.og.application.OgScraper;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Authenticated operations use the actual server; only external website metadata is stubbed. */
public abstract class OperationalHttpJourneySupport extends DockerHttpTest {

  @LocalServerPort private int port;
  @Autowired private QueryInterceptor interceptor;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected JsonMapper json;
  @Autowired protected UserRepository users;
  @Autowired protected JwtTokenService jwt;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoBean protected OgScraper externalMetadata;

  protected TransactionTemplate transactions;
  protected HttpQueryContracts contracts;
  private final List<HttpQueryContracts.CapturedRequest<?>> captured = new ArrayList<>();
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @BeforeEach
  void prepareJourney() {
    transactions = new TransactionTemplate(transactionManager);
    contracts = new HttpQueryContracts(interceptor, this::awaitAsyncWork);
    captured.clear();
    when(externalMetadata.fetch(anyString())).thenReturn(OgMetadata.empty());
  }

  @AfterEach
  void checkEveryCapturedStep() {
    assertAll(
        "SQL contracts for the complete user journey",
        captured.stream().map(request -> () -> contracts.verify(request)));
  }

  protected Actor actor(String prefix, boolean admin) {
    String handle = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    UserEntity user =
        transactions.execute(
            transaction -> {
              UserEntity entity = new UserEntity(handle + "@example.com", "google", handle);
              entity.claimUsername(handle);
              if (admin) entity.promoteToAdmin();
              return users.save(entity);
            });
    return new Actor(
        user.getId(), handle, jwt.createAccessToken(user.getId(), admin ? "ADMIN" : "USER"));
  }

  protected JsonNode step(
      String id, String method, String path, Actor actor, Object body, int status)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("X-Query-Contract-ID", id)
            .header("User-Agent", "Mozilla/5.0 Chrome/130.0.0.0 Safari/537.36")
            .header("Accept-Language", "ko")
            .timeout(Duration.ofSeconds(30));
    if (actor != null) request.header("Authorization", "Bearer " + actor.token());
    HttpRequest.BodyPublisher payload = HttpRequest.BodyPublishers.noBody();
    if (body != null) {
      request.header("Content-Type", "application/json");
      payload = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
    }
    HttpRequest built = request.method(method, payload).build();
    var result =
        contracts.capture(id, () -> client.send(built, HttpResponse.BodyHandlers.ofString()));
    captured.add(result);
    assertThat(result.response().statusCode())
        .as("%s %s: %s", method, path, result.response().body())
        .isEqualTo(status);
    if (result.response().body().isBlank()) return json.nullNode();
    return json.readTree(result.response().body());
  }

  protected long count(String table, String condition, Object... args) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE " + condition, Long.class, args);
  }

  protected void deliverBackgroundWork(String id, Runnable work) throws Exception {
    captured.add(
        contracts.captureBackgroundDelivery(
            id,
            () -> {
              work.run();
              return "delivered";
            }));
  }

  protected record Actor(long id, String username, String token) {}
}
