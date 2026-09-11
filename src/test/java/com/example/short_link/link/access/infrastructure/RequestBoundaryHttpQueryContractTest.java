package com.example.short_link.link.access.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.access.application.TurnstileProperties;
import com.example.short_link.link.access.application.TurnstileVerifier;
import com.example.short_link.link.presentation.LinkJourneyHttpSupport;
import com.example.short_link.link.stats.application.ClickFlusher;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/** Actual request binding and password unlock, with only the remote provider replaced by HTTP. */
@Import(RequestBoundaryHttpQueryContractTest.ProviderConfiguration.class)
@TestPropertySource(
    properties = {
      "short-link.turnstile.site-key=local-site",
      "short-link.turnstile.secret=local-secret"
    })
class RequestBoundaryHttpQueryContractTest extends LinkJourneyHttpSupport {
  @Autowired private ProviderStub provider;
  @Autowired private ClickFlusher clickFlusher;

  @Test
  void malformedNumbersReturn400AndTheOwnerCanStillReadThePersistedLink() throws Exception {
    String code = createLink("binding-link-create");
    for (String parameter : new String[] {"page", "size"}) {
      var problem =
          request(
              "binding-feed-invalid-" + parameter,
              null,
              "GET",
              "/api/v1/public/posts?" + parameter + "=abc",
              null,
              400);
      assertThat(problem.path("code").asText()).isEqualTo("INVALID_ARGUMENT");
      assertThat(problem.path("parameter").asText()).isEqualTo(parameter);
    }
    var invalidSize =
        request("binding-links-invalid-size", owner, "GET", "/api/v1/links/me?size=abc", null, 400);
    assertThat(invalidSize.path("code").asText()).isEqualTo("INVALID_ARGUMENT");
    assertThat(invalidSize.path("parameter").asText()).isEqualTo("size");
    var overflow =
        request(
            "binding-links-overflow-size",
            owner,
            "GET",
            "/api/v1/links/me?size=999999999999999999999",
            null,
            400);
    assertThat(overflow.path("code").asText()).isEqualTo("INVALID_ARGUMENT");

    var list =
        request(
            "binding-links-valid-after-rejection",
            owner,
            "GET",
            "/api/v1/links/me?size=20",
            null,
            200);
    assertThat(list.toString()).contains(code);
    assertThat(
            number(
                "SELECT COUNT(*) FROM link WHERE short_code = ? AND user_id = ?", code, owner.id()))
        .isEqualTo(1);
  }

  @Test
  void onlyAProviderBooleanSuccessUnlocksTheLinkAndPersistsTheVisit() throws Exception {
    String code = createLink("turnstile-link-create");
    long linkId = linkId(code);
    request(
        "turnstile-link-protect",
        owner,
        "PATCH",
        "/api/v1/links/" + code + "/protection",
        Map.of("password", "visit-password", "maxViews", 1),
        200);
    assertThat(text("SELECT password_hash FROM link WHERE id = ?", linkId)).isNotBlank();
    int requestsBefore = provider.requestCount.get();

    provider.body.set("{\"success\":false,\"details\":{\"success\":true}}");
    unlock("turnstile-unlock-nested-success-rejected", code, 401);
    assertThat(number("SELECT view_count FROM link WHERE id = ?", linkId)).isZero();
    assertThat(number("SELECT COUNT(*) FROM click_event WHERE link_id = ?", linkId)).isZero();

    provider.body.set("{\"success\":\"true\"}");
    unlock("turnstile-unlock-string-success-rejected", code, 401);
    assertThat(number("SELECT view_count FROM link WHERE id = ?", linkId)).isZero();

    provider.body.set("{\n  \"hostname\": \"example.test\",\n  \"success\" : true\n}");
    var unlocked =
        raw(
            "turnstile-unlock-json-success",
            null,
            "POST",
            "/" + code,
            "password=visit-password&cf-turnstile-response=local-proof",
            "application/x-www-form-urlencoded",
            200);
    assertThat(new String(unlocked.body(), StandardCharsets.UTF_8)).contains("example.com");
    assertThat(provider.requestCount.get() - requestsBefore).isEqualTo(3);
    assertThat(provider.lastForm.get()).contains("secret=local-secret", "response=local-proof");
    assertThat(number("SELECT view_count FROM link WHERE id = ?", linkId)).isEqualTo(1);

    captures.add(
        contracts.captureBackgroundDelivery(
            "turnstile-click-worker-persist",
            () -> {
              clickFlusher.flush();
              return "completed";
            }));
    assertThat(number("SELECT COUNT(*) FROM click_event WHERE link_id = ?", linkId)).isEqualTo(1);
  }

  private void unlock(String contractId, String code, int expectedStatus) throws Exception {
    raw(
        contractId,
        null,
        "POST",
        "/" + code,
        "password=visit-password&cf-turnstile-response=local-proof",
        "application/x-www-form-urlencoded",
        expectedStatus);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ProviderConfiguration {
    @Bean(destroyMethod = "close")
    ProviderStub providerStub() throws IOException {
      return new ProviderStub();
    }

    @Bean
    @Primary
    TurnstileVerifier localTurnstileVerifier(
        TurnstileProperties properties, ProviderStub provider) {
      return new CloudflareTurnstileVerifier(properties, provider.endpoint());
    }
  }

  static final class ProviderStub implements AutoCloseable {
    private final HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>("{\"success\":false}");
    private final AtomicReference<String> lastForm = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    ProviderStub() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/siteverify",
          exchange -> {
            try (exchange) {
              requestCount.incrementAndGet();
              lastForm.set(
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
              byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
              exchange.getResponseHeaders().add("Content-Type", "application/json");
              exchange.sendResponseHeaders(200, response.length);
              exchange.getResponseBody().write(response);
            }
          });
      server.start();
    }

    URI endpoint() {
      return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify");
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
