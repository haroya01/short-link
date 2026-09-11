package com.example.short_link.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Real HTTP and committed fixtures shared by account, profile, and notification journeys. */
public abstract class AccountHttpJourneySupport extends DockerHttpTest {
  @LocalServerPort private int port;
  @Autowired protected UserRepository users;
  @Autowired protected JwtTokenService jwt;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected JsonMapper json;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private QueryInterceptor interceptor;
  protected TransactionTemplate transactions;
  protected UserEntity owner;
  protected UserEntity stranger;
  protected String token;
  protected String strangerToken;
  private HttpQueryContracts contracts;
  private final List<HttpQueryContracts.CapturedRequest<HttpResponse<String>>> captures =
      new ArrayList<>();
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @BeforeEach
  void commitJourneyUsers() {
    transactions = new TransactionTemplate(transactionManager);
    contracts = new HttpQueryContracts(interceptor, this::awaitAsyncWork);
    captures.clear();
    owner = createUser();
    stranger = createUser();
    token = jwt.createAccessToken(owner.getId(), "USER");
    strangerToken = jwt.createAccessToken(stranger.getId(), "USER");
  }

  protected UserEntity createUser() {
    String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    return transactions.execute(
        status -> {
          UserEntity user = new UserEntity(name + "@example.com", "google", name);
          user.claimUsername(name);
          return users.save(user);
        });
  }

  protected HttpResponse<String> call(
      String id, String method, String path, Object body, String auth, int expected)
      throws Exception {
    return callWithHeaders(
        id,
        method,
        path,
        body,
        auth == null ? Map.of() : Map.of("Authorization", "Bearer " + auth),
        expected);
  }

  protected HttpResponse<String> callWithHeaders(
      String id, String method, String path, Object body, Map<String, String> headers, int expected)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(30))
            .header("X-Query-Contract-ID", id);
    if (headers.keySet().stream().noneMatch(name -> name.equalsIgnoreCase("Accept-Language"))) {
      builder.header("Accept-Language", "ko");
    }
    headers.forEach(builder::header);
    if (body != null) builder.header("Content-Type", "application/json");
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    var captured =
        contracts.capture(
            id, () -> http.send(builder.build(), HttpResponse.BodyHandlers.ofString()));
    captures.add(captured);
    assertThat(captured.response().statusCode())
        .as("%s: %s", id, captured.response().body())
        .isEqualTo(expected);
    return captured.response();
  }

  protected JsonNode body(HttpResponse<String> response) {
    return json.readTree(response.body());
  }

  protected long count(String sql, Object... arguments) {
    return jdbc.queryForObject(sql, Long.class, arguments);
  }

  @AfterEach
  void verifyEveryCapturedContract() {
    assertAll(
        "HTTP query contracts",
        captures.stream().map(captured -> () -> contracts.verify(captured)));
  }
}
