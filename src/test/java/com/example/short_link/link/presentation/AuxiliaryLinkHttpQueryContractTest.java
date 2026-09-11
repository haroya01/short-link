package com.example.short_link.link.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.TxtResolver;
import com.example.short_link.link.stats.application.ClickFlusher;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

/** Anonymous adoption, import, DNS verification and live click delivery use real HTTP. */
class AuxiliaryLinkHttpQueryContractTest extends LinkJourneyHttpSupport {
  @MockitoBean private TxtResolver remoteDns;
  @Autowired private StringRedisTemplate redis;
  @Autowired private ClickFlusher clickFlusher;
  @Autowired private ClickEventRepository clicks;

  @Test
  void anonymousVisitorCreatesThenAdoptsLinkAndOpensAuthenticatedStreams() throws Exception {
    var challenge = request("link-pow-challenge", null, "GET", "/api/v1/pow/challenge", null, 200);
    String challengeId = challenge.path("challenge").asText();
    assertThat(redis.hasKey("pow:challenge:" + challengeId)).isTrue();
    assertThat(redis.getExpire("pow:challenge:" + challengeId)).isBetween(1L, 300L);
    var anonymous =
        request(
            "link-create-anonymous",
            null,
            "POST",
            "/api/v1/links",
            Map.of("url", "https://example.com/anonymous-journey"),
            201);
    String code = anonymous.path("shortCode").asText();
    long id = linkId(code);
    String claim = anonymous.path("claimToken").asText();
    assertThat(text("SELECT claim_token FROM link WHERE id = ?", id)).isEqualTo(claim);
    assertThat(
            number(
                "SELECT COUNT(*) FROM link WHERE id = ? AND user_id IS NULL AND expires_at IS NOT NULL",
                id))
        .isEqualTo(1);
    String linkPath = "/api/v1/links/" + code;
    openStream("link-stream-anonymous", linkPath + "/stream?claimToken=" + claim).close();
    assertThat(
            request(
                    "link-claim-anonymous",
                    owner,
                    "POST",
                    "/api/v1/users/me/claim-anonymous",
                    Map.of("claimTokens", List.of(claim)),
                    200)
                .path("claimed")
                .asLong())
        .isEqualTo(1);
    assertThat(number("SELECT user_id FROM link WHERE id = ?", id)).isEqualTo(owner.id());
    assertThat(
            number(
                "SELECT COUNT(*) FROM link WHERE id = ? AND claim_token IS NULL AND expires_at IS NULL",
                id))
        .isEqualTo(1);
    assertThat(
            request(
                    "link-claim-replay",
                    stranger,
                    "POST",
                    "/api/v1/users/me/claim-anonymous",
                    Map.of("claimTokens", List.of(claim)),
                    200)
                .path("claimed")
                .asLong())
        .isZero();
    raw(
        "link-stream-claim-revoked",
        null,
        "GET",
        linkPath + "/stream?claimToken=" + claim,
        null,
        null,
        401);
    var token = request("link-stream-token", owner, "POST", linkPath + "/stream-token", null, 200);
    var accountToken =
        request(
            "link-account-stream-token",
            owner,
            "POST",
            "/api/v1/users/me/clicks/stream-token",
            null,
            200);
    try (var linkStream =
            openStream(
                "link-stream-authenticated",
                linkPath + "/stream?streamToken=" + token.path("streamToken").asText());
        var accountStream =
            openStream(
                "link-account-stream",
                "/api/v1/users/me/clicks/stream?streamToken="
                    + accountToken.path("streamToken").asText())) {
      var redirect = raw("link-redirect", null, "GET", "/" + code, null, null, 302);
      assertThat(redirect.headers().firstValue("Location"))
          .contains("https://example.com/anonymous-journey");
      captures.add(
          contracts.captureBackgroundDelivery(
              "link-redirect-worker-persist",
              () -> {
                clickFlusher.flush();
                return "completed";
              }));
      assertThat(number("SELECT COUNT(*) FROM click_event WHERE link_id = ?", id)).isEqualTo(1);
      ClickEventEntity committed = clicks.findEventsByLinkIdLatest(id, 1).getFirst();
      JsonNode linkEvent = json.readTree(linkStream.awaitEvent("click"));
      JsonNode accountEvent = json.readTree(accountStream.awaitEvent("click"));
      assertClickPayload(linkEvent, committed);
      assertClickPayload(accountEvent, committed);
      assertThat(accountEvent.path("shortCode").asText()).isEqualTo(code);
      assertThat(accountEvent.path("occurredAt")).isEqualTo(linkEvent.path("occurredAt"));
    }
    byte[] png = raw("link-og-card", null, "GET", "/" + code + "/og.png", null, null, 200).body();
    assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    request(
        "link-public-preview",
        null,
        "GET",
        "/api/v1/public/link-preview?url=https%3A%2F%2Fexample.com%2Fanonymous-journey",
        null,
        200);
    request("link-public-service-stats", null, "GET", "/api/v1/public/stats", null, 200);
    assertThat(
            raw("link-root-redirect", null, "GET", "/", null, null, 301)
                .headers()
                .firstValue("Location"))
        .isPresent();
  }

  @Test
  void ownerImportsMultipleLinksAndVerifiesDomainOwnershipBeforeDeletingDomain() throws Exception {
    String csv =
        "url,custom_code\nhttps://example.com/import-a,ImportHttpA\nhttps://example.com/import-b,ImportHttpB\n";
    String boundary = "HttpJourneyBoundary";
    String multipart =
        "--"
            + boundary
            + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"links.csv\"\r\n"
            + "Content-Type: text/csv\r\n\r\n"
            + csv
            + "\r\n--"
            + boundary
            + "--\r\n";
    var imported =
        raw(
            "link-bulk-import",
            owner,
            "POST",
            "/api/v1/links/bulk",
            multipart,
            "multipart/form-data; boundary=" + boundary,
            200);
    assertThat(imported.headers().firstValue("X-Bulk-Ok")).contains("2");
    assertThat(
            number(
                "SELECT COUNT(*) FROM link WHERE user_id = ? AND short_code IN ('ImportHttpA', 'ImportHttpB')",
                owner.id()))
        .isEqualTo(2);
    assertThat(new String(imported.body(), StandardCharsets.UTF_8))
        .contains("ImportHttpA", "ImportHttpB");

    var domain =
        request(
            "custom-domain-register",
            owner,
            "POST",
            "/api/v1/custom-domains",
            Map.of("domain", "go.example.test"),
            201);
    long id = domain.path("id").asLong();
    String token = domain.path("verificationToken").asText();
    assertThat(number("SELECT verified FROM custom_domain WHERE id = ?", id)).isZero();
    assertThat(text("SELECT verification_token FROM custom_domain WHERE id = ?", id))
        .isEqualTo(token);
    assertThat(
            request("custom-domain-list", owner, "GET", "/api/v1/custom-domains", null, 200)
                .toString())
        .contains("go.example.test");
    when(remoteDns.lookup("_kurl-verify.go.example.test")).thenReturn(List.of("wrong-token"));
    request(
        "custom-domain-verification-missing",
        owner,
        "POST",
        "/api/v1/custom-domains/" + id + "/verify",
        null,
        422);
    assertThat(number("SELECT verified FROM custom_domain WHERE id = ?", id)).isZero();
    when(remoteDns.lookup("_kurl-verify.go.example.test")).thenReturn(List.of(token));
    request(
        "custom-domain-verify",
        owner,
        "POST",
        "/api/v1/custom-domains/" + id + "/verify",
        null,
        200);
    assertThat(number("SELECT verified FROM custom_domain WHERE id = ?", id)).isEqualTo(1);
    request(
        "custom-domain-delete-foreign-denied",
        stranger,
        "DELETE",
        "/api/v1/custom-domains/" + id,
        null,
        404);
    assertThat(number("SELECT COUNT(*) FROM custom_domain WHERE id = ?", id)).isEqualTo(1);
    request("custom-domain-delete", owner, "DELETE", "/api/v1/custom-domains/" + id, null, 204);
    assertThat(number("SELECT COUNT(*) FROM custom_domain WHERE id = ?", id)).isZero();
  }

  private void assertClickPayload(JsonNode payload, ClickEventEntity committed) {
    // click_event.clicked_at stores whole seconds, while SSE retains the original Instant.
    assertThat(
            Duration.between(
                    committed.getClickedAt(), Instant.parse(payload.path("occurredAt").asText()))
                .abs())
        .isLessThan(Duration.ofSeconds(1));
    assertThat(payload.path("countryCode").asText())
        .isEqualTo(Objects.toString(committed.getCountryCode(), ""));
    assertThat(payload.path("deviceClass").asText())
        .isEqualTo(Objects.toString(committed.getDeviceClass(), ""));
    assertThat(payload.path("channel").asText())
        .isEqualTo(Objects.toString(committed.getReferrerHost(), ""));
    assertThat(payload.path("bot").asBoolean()).isEqualTo(committed.isBot());
  }

  private EventStream openStream(String contractId, String path) throws Exception {
    var captured =
        contracts.capture(
            contractId,
            () -> {
              HttpRequest request =
                  HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                      .timeout(Duration.ofSeconds(10))
                      .header("X-Query-Contract-ID", contractId)
                      .GET()
                      .build();
              HttpResponse<InputStream> response =
                  http.send(request, HttpResponse.BodyHandlers.ofInputStream());
              EventStream stream = new EventStream(response.body());
              try {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(json.readTree(stream.awaitEvent("ready")).path("ok").asBoolean())
                    .isTrue();
                return stream;
              } catch (Exception | AssertionError failure) {
                stream.close();
                throw failure;
              }
            });
    captures.add(captured);
    return captured.response();
  }

  /**
   * Keeps the HTTP response open between frames and bounds a missing-event failure to ten seconds.
   */
  private static final class EventStream implements AutoCloseable {
    private final InputStream body;
    private final BufferedReader reader;

    private EventStream(InputStream body) {
      this.body = body;
      this.reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
    }

    private String awaitEvent(String expectedName) throws Exception {
      FutureTask<String> pending = new FutureTask<>(() -> readEvent(expectedName));
      Thread.startVirtualThread(pending);
      try {
        return pending.get(10, TimeUnit.SECONDS);
      } catch (TimeoutException timeout) {
        body.close();
        pending.cancel(true);
        throw new AssertionError(
            "SSE did not deliver the " + expectedName + " event within ten seconds", timeout);
      }
    }

    private String readEvent(String expectedName) throws IOException {
      String name = null;
      StringBuilder data = new StringBuilder();
      for (String line; (line = reader.readLine()) != null; ) {
        if (line.isEmpty()) {
          if (name == null && data.isEmpty()) continue;
          assertThat(name).isEqualTo(expectedName);
          assertThat(data.toString()).isNotEmpty();
          return data.toString();
        }
        if (line.startsWith("event:")) name = line.substring("event:".length()).stripLeading();
        if (line.startsWith("data:")) {
          if (!data.isEmpty()) data.append('\n');
          data.append(line.substring("data:".length()).stripLeading());
        }
      }
      throw new AssertionError("SSE closed before delivering the " + expectedName + " event");
    }

    @Override
    public void close() throws IOException {
      body.close();
    }
  }
}
