package com.example.short_link.link.access.infrastructure;

import com.example.short_link.link.access.application.TurnstileProperties;
import com.example.short_link.link.access.application.TurnstileVerifier;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class CloudflareTurnstileVerifier implements TurnstileVerifier {

  private static final URI SITEVERIFY =
      URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");
  private static final JsonMapper JSON =
      JsonMapper.builder()
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .build();

  private final TurnstileProperties props;
  private final URI endpoint;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  @Autowired
  public CloudflareTurnstileVerifier(TurnstileProperties props) {
    this(props, SITEVERIFY);
  }

  CloudflareTurnstileVerifier(TurnstileProperties props, URI endpoint) {
    this.props = props;
    this.endpoint = endpoint;
  }

  @Override
  public boolean enabled() {
    return props.verifyEnabled();
  }

  @Override
  public boolean verify(String token, String remoteIp) {
    if (!props.verifyEnabled()) {
      return true;
    }
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      HttpResponse<String> response =
          http.send(verificationRequest(token, remoteIp), HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200 && reportsSuccess(response.body());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      log.warn("Turnstile verification call failed: {}", e.toString());
      return false;
    }
  }

  private HttpRequest verificationRequest(String token, String remoteIp) {
    StringBuilder form =
        new StringBuilder("secret=")
            .append(enc(props.secret()))
            .append("&response=")
            .append(enc(token));
    if (remoteIp != null && !remoteIp.isBlank()) {
      form.append("&remoteip=").append(enc(remoteIp));
    }
    return HttpRequest.newBuilder(endpoint)
        .timeout(Duration.ofSeconds(4))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
        .build();
  }

  private boolean reportsSuccess(String body) {
    if (body == null || body.isBlank()) return false;
    JsonNode response = JSON.readTree(body);
    if (!response.isObject()) return false;
    JsonNode success = response.path("success");
    return success.isBoolean() && success.booleanValue();
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
