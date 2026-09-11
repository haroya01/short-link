package com.example.short_link.notification.infrastructure;

import com.example.short_link.notification.application.push.ApnsProperties;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * APNs HTTP/2 직발송(외부 SDK 없음). 인증은 .p8 키로 서명한 ES256 JWT — 50분 캐시(애플 권장 20~60분 창). 발송은 작은 전용 풀에서
 * fire-and-forget으로 수행하고 전송 실패는 로그로 남긴다. 기기 토큰 조회·payload 직렬화는 호출 스레드에서 실행되며 실패가 호출자에게 전파된다.
 * 410(Unregistered)·BadDeviceToken 은 그 자리에서 토큰 폐기.
 */
@Component
@Slf4j
public class ApnsPushSender implements PushSender {

  /** 앱이 이 category 에 "통계 보기" 액션 버튼을 묶어 뒀다(UNNotificationCategory) — shortCode 있는 알림에만 단다. */
  private static final String LINK_STATS_CATEGORY = "LINK_STATS";

  private final ApnsProperties props;
  private final DeviceTokenRepository deviceTokens;
  private final JsonMapper jsonMapper;
  private final HttpClient http;
  private final Executor executor;
  private final ApnsTokenProvider tokenProvider;

  @Autowired
  public ApnsPushSender(
      ApnsProperties props,
      DeviceTokenRepository deviceTokens,
      JsonMapper jsonMapper,
      ApnsTokenProvider tokenProvider) {
    this(
        props,
        deviceTokens,
        jsonMapper,
        tokenProvider,
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(5))
            .build(),
        Executors.newFixedThreadPool(
            2,
            runnable -> {
              Thread thread = new Thread(runnable, "apns-push");
              thread.setDaemon(true);
              return thread;
            }));
  }

  ApnsPushSender(
      ApnsProperties props,
      DeviceTokenRepository deviceTokens,
      JsonMapper jsonMapper,
      ApnsTokenProvider tokenProvider,
      HttpClient http,
      Executor executor) {
    this.props = props;
    this.deviceTokens = deviceTokens;
    this.jsonMapper = jsonMapper;
    this.tokenProvider = tokenProvider;
    this.http = http;
    this.executor = executor;
    if (!tokenProvider.configured()) {
      log.info("APNs not configured (short-link.apns.*) — push sender is a no-op");
    }
  }

  @Override
  public void send(Long recipientUserId, PushMessage message) {
    if (!tokenProvider.configured()) return;
    dispatch(deviceTokens.tokensForUser(recipientUserId), message);
  }

  @Override
  public void sendToAll(Collection<Long> recipientUserIds, PushMessage message) {
    if (!tokenProvider.configured() || recipientUserIds.isEmpty()) return;
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
              .header("authorization", "bearer " + tokenProvider.token())
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

  /**
   * aps.alert/sound 는 그대로 두고(구버전 앱 호환) 라우팅 힌트만 얹는다: type·shortCode 는 aps 형제 최상위 키로, shortCode 가 있는
   * 알림만 category="LINK_STATS" 를 달아 앱이 "통계 보기" 액션을 붙인다. 값이 없는 키는 통째로 생략한다.
   */
  String payloadJson(PushMessage message) {
    var alert = new java.util.LinkedHashMap<String, Object>();
    alert.put("title", message.title());
    if (message.subtitle() != null) alert.put("subtitle", message.subtitle());
    alert.put("body", message.body());

    var aps = new java.util.LinkedHashMap<String, Object>();
    aps.put("alert", alert);
    aps.put("sound", "default");
    if (message.shortCode() != null) aps.put("category", LINK_STATS_CATEGORY);

    var root = new java.util.LinkedHashMap<String, Object>();
    root.put("aps", aps);
    if (message.type() != null) root.put("type", message.type());
    if (message.shortCode() != null) root.put("shortCode", message.shortCode());
    return jsonMapper.writeValueAsString(root);
  }

  private static String tail(String token) {
    return token.length() <= 6 ? token : token.substring(token.length() - 6);
  }
}
