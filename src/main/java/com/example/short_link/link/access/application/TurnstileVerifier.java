package com.example.short_link.link.access.application;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Verifies a Cloudflare Turnstile token against the siteverify endpoint. No-op (always passes) when
 * no secret is configured, so the password gate keeps working until the owner provisions keys.
 * Fail-closed when configured: if the verification call errors or the token is bad, unlock is
 * denied (the password is still the primary gate; this only adds bot resistance).
 */
@Slf4j
@Component
public class TurnstileVerifier {

  private static final URI SITEVERIFY =
      URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");

  private final TurnstileProperties props;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  public TurnstileVerifier(TurnstileProperties props) {
    this.props = props;
  }

  public boolean enabled() {
    return props.verifyEnabled();
  }

  /** True if the challenge passes, or if Turnstile isn't configured. */
  public boolean verify(String token, String remoteIp) {
    if (!props.verifyEnabled()) {
      return true;
    }
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      StringBuilder form =
          new StringBuilder("secret=")
              .append(enc(props.secret()))
              .append("&response=")
              .append(enc(token));
      if (remoteIp != null && !remoteIp.isBlank()) {
        form.append("&remoteip=").append(enc(remoteIp));
      }
      HttpRequest request =
          HttpRequest.newBuilder(SITEVERIFY)
              .timeout(Duration.ofSeconds(4))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      // Lightweight check — avoid pulling a JSON parser onto the unlock hot path.
      return response.statusCode() == 200
          && response.body() != null
          && response.body().contains("\"success\":true");
    } catch (Exception e) {
      log.warn("Turnstile verification call failed: {}", e.toString());
      return false;
    }
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
