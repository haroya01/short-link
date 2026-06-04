package com.example.short_link.common.webhook;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The one place an outbound webhook is actually signed and POSTed. Shared by every webhook source
 * (link-click hooks, blog-interaction hooks) so the SSRF guard, HMAC-SHA256 signature, headers,
 * timeouts and delivery metrics are identical everywhere. Stateless: it never touches a hook entity
 * — it returns a {@link Result} the caller maps onto its own record-success/record-failure surface.
 */
public final class WebhookSender {

  private static final String USER_AGENT = "kurl-webhook/1.0 (+https://kurl.me)";
  private static final String SIGNATURE_HEADER = "X-Kurl-Signature";
  private static final String EVENT_HEADER = "X-Kurl-Event";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  private WebhookSender() {}

  public enum Outcome {
    OK,
    NON_2XX,
    EXCEPTION,
    BLOCKED,
    SIGN_ERROR
  }

  /**
   * The delivery outcome. {@code statusCode} is the HTTP status when one was received, else null.
   */
  public record Result(Outcome outcome, Integer statusCode, String error) {
    public boolean ok() {
      return outcome == Outcome.OK;
    }
  }

  /**
   * Sign (when {@code sign}) and POST {@code body} to {@code url}. {@code eventType} rides in the
   * {@code X-Kurl-Event} header. Delivery metrics are emitted on {@code registry} with the same
   * tags across all sources. Never throws — every failure becomes a {@link Result}.
   */
  public static Result send(
      HttpFetcher httpFetcher,
      MeterRegistry registry,
      String url,
      String secret,
      boolean sign,
      String body,
      String eventType) {
    Resolved resolved = PublicHttpUrlGuard.resolve(url).orElse(null);
    if (resolved == null) {
      registry.counter("webhook.delivery", "result", "blocked").increment();
      return new Result(
          Outcome.BLOCKED,
          null,
          "url is not reachable as a public host (DNS or scheme check failed)");
    }

    String signature = null;
    if (sign) {
      try {
        signature = sign(body, secret);
      } catch (Exception e) {
        registry.counter("webhook.delivery", "result", "sign_error").increment();
        return new Result(Outcome.SIGN_ERROR, null, "signature failed: " + e.getMessage());
      }
    }

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("User-Agent", USER_AGENT);
    headers.put(EVENT_HEADER, eventType);
    if (sign) {
      headers.put(SIGNATURE_HEADER, "sha256=" + signature);
    }

    Timer.Sample sample = Timer.start(registry);
    String resultTag = "exception";
    try {
      HttpFetcher.Response response =
          httpFetcher.fetch(
              HttpFetcher.Request.post(
                  resolved,
                  headers,
                  body.getBytes(StandardCharsets.UTF_8),
                  "application/json",
                  CONNECT_TIMEOUT,
                  READ_TIMEOUT,
                  0));
      int status = response.status();
      if (status / 100 == 2) {
        registry.counter("webhook.delivery", "result", "ok").increment();
        resultTag = "ok";
        return new Result(Outcome.OK, status, null);
      }
      registry.counter("webhook.delivery", "result", "non_2xx").increment();
      resultTag = "non_2xx";
      return new Result(Outcome.NON_2XX, status, "non-2xx response (" + status + ")");
    } catch (Exception e) {
      registry.counter("webhook.delivery", "result", "exception").increment();
      return new Result(
          Outcome.EXCEPTION, null, e.getClass().getSimpleName() + ": " + e.getMessage());
    } finally {
      sample.stop(registry.timer("outbound.http", "client", "webhook", "result", resultTag));
    }
  }

  private static String sign(String body, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] sig = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder(sig.length * 2);
    for (byte b : sig) hex.append(String.format("%02x", b));
    return hex.toString();
  }
}
