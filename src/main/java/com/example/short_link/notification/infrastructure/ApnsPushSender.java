package com.example.short_link.notification.infrastructure;

import com.example.short_link.notification.application.push.ApnsProperties;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * APNs HTTP/2 직발송(외부 SDK 없음). 인증은 .p8 키로 서명한 ES256 JWT — 50분 캐시(애플 권장 20~60분 창). 발송은 작은 전용 풀에서
 * fire-and-forget: 알림 저장 트랜잭션을 절대 붙잡지 않고, 실패는 로그로만 남긴다(인앱 벨이 진실의 원천, 푸시는 보조 채널).
 * 410(Unregistered)·BadDeviceToken 은 그 자리에서 토큰 폐기.
 */
@Component
@Slf4j
public class ApnsPushSender implements PushSender {

  private final ApnsProperties props;
  private final DeviceTokenRepository deviceTokens;
  private final JsonMapper jsonMapper;
  private final HttpClient http;
  private final ExecutorService executor;
  private final PrivateKey signingKey;

  private volatile String cachedJwt;
  private volatile Instant jwtIssuedAt = Instant.EPOCH;

  public ApnsPushSender(
      ApnsProperties props, DeviceTokenRepository deviceTokens, JsonMapper jsonMapper) {
    this.props = props;
    this.deviceTokens = deviceTokens;
    this.jsonMapper = jsonMapper;
    this.http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    this.executor =
        Executors.newFixedThreadPool(
            2,
            runnable -> {
              Thread thread = new Thread(runnable, "apns-push");
              thread.setDaemon(true);
              return thread;
            });
    this.signingKey = props.configured() ? parseKey(props.privateKey()) : null;
    if (signingKey == null) {
      log.info("APNs not configured (short-link.apns.*) — push sender is a no-op");
    }
  }

  @Override
  public void send(Long recipientUserId, PushMessage message) {
    if (signingKey == null) return;
    dispatch(deviceTokens.tokensForUser(recipientUserId), message);
  }

  @Override
  public void sendToAll(Collection<Long> recipientUserIds, PushMessage message) {
    if (signingKey == null || recipientUserIds.isEmpty()) return;
    dispatch(deviceTokens.tokensForUsers(recipientUserIds), message);
  }

  private void dispatch(List<String> tokens, PushMessage message) {
    if (tokens.isEmpty()) return;
    String payload = payloadJson(message);
    for (String token : tokens) {
      executor.execute(() -> post(token, payload));
    }
  }

  private void post(String token, String payload) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(props.host() + "/3/device/" + token))
              .header("authorization", "bearer " + jwt())
              .header("apns-topic", props.bundleId())
              .header("apns-push-type", "alert")
              .timeout(Duration.ofSeconds(10))
              .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 410
          || (response.statusCode() == 400 && response.body().contains("BadDeviceToken"))) {
        deviceTokens.deleteByToken(token);
      } else if (response.statusCode() >= 400) {
        log.debug("APNs {} for token …{}: {}", response.statusCode(), tail(token), response.body());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.debug("APNs send failed for token …{}: {}", tail(token), e.toString());
    }
  }

  private String payloadJson(PushMessage message) {
    var alert = new java.util.LinkedHashMap<String, Object>();
    alert.put("title", message.title());
    if (message.subtitle() != null) alert.put("subtitle", message.subtitle());
    alert.put("body", message.body());
    return jsonMapper.writeValueAsString(
        java.util.Map.of("aps", java.util.Map.of("alert", alert, "sound", "default")));
  }

  synchronized String jwt() {
    if (cachedJwt != null && jwtIssuedAt.isAfter(Instant.now().minus(Duration.ofMinutes(50)))) {
      return cachedJwt;
    }
    Instant now = Instant.now();
    String header = b64url("{\"alg\":\"ES256\",\"kid\":\"" + props.keyId() + "\"}");
    String claims =
        b64url("{\"iss\":\"" + props.teamId() + "\",\"iat\":" + now.getEpochSecond() + "}");
    String signingInput = header + "." + claims;
    cachedJwt = signingInput + "." + sign(signingInput);
    jwtIssuedAt = now;
    return cachedJwt;
  }

  private String sign(String input) {
    try {
      Signature signature = Signature.getInstance("SHA256withECDSA");
      signature.initSign(signingKey);
      signature.update(input.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(derToJose(signature.sign()));
    } catch (Exception e) {
      throw new IllegalStateException("APNs JWT signing failed", e);
    }
  }

  /** SHA256withECDSA 는 DER 시퀀스를 내놓는다 — JOSE 는 r·s 각 32바이트 원시 연결을 원한다. */
  static byte[] derToJose(byte[] der) {
    int rLength = der[3];
    int rOffset = 4;
    int sLength = der[rOffset + rLength + 1];
    int sOffset = rOffset + rLength + 2;
    byte[] jose = new byte[64];
    copyTrimmed(der, rOffset, rLength, jose, 0);
    copyTrimmed(der, sOffset, sLength, jose, 32);
    return jose;
  }

  private static void copyTrimmed(byte[] src, int offset, int length, byte[] dst, int dstStart) {
    // DER 정수는 부호 패딩(앞 0x00)이 붙거나 32바이트보다 짧을 수 있다 — 우측 정렬로 복사.
    int skip = Math.max(0, length - 32);
    int copy = Math.min(length, 32);
    System.arraycopy(src, offset + skip, dst, dstStart + (32 - copy), copy);
  }

  private static PrivateKey parseKey(String pem) {
    try {
      String base64 =
          pem.replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s", "");
      byte[] der = Base64.getDecoder().decode(base64);
      return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalStateException("APNs private key (.p8 PEM) is malformed", e);
    }
  }

  private static String b64url(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String tail(String token) {
    return token.length() <= 6 ? token : token.substring(token.length() - 6);
  }
}
