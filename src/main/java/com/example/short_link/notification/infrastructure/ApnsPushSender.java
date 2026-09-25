package com.example.short_link.notification.infrastructure;

import com.example.short_link.notification.application.push.ApnsProperties;
import com.example.short_link.notification.application.push.PushRoute;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.user.domain.DeviceTarget;
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
 * 전송은 전용 풀에서 실행하며 실패는 로그로 남긴다. 기기 조회·직렬화 실패는 호출자에게 전파된다. 410(Unregistered)과 BadDeviceToken은 기기 토큰을
 * 폐기한다. 메시지의 대상 앱 topic 으로만 보내며, topic 을 모르는 기존 토큰은 성공하면 그 topic 으로, DeviceTokenNotForTopic 이면 다른 앱
 * topic 으로 기록해 다음부터 고른다.
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
    dispatch(deviceTokens.targetsForUser(recipientUserId), message);
  }

  @Override
  public void sendToAll(Collection<Long> recipientUserIds, PushMessage message) {
    if (!tokenProvider.configured() || recipientUserIds.isEmpty()) return;
    dispatch(deviceTokens.targetsForUsers(recipientUserIds), message);
  }

  private void dispatch(List<DeviceTarget> targets, PushMessage message) {
    String topic = props.topicFor(message.app());
    List<DeviceTarget> reachable =
        targets.stream().filter(t -> t.topic() == null || t.topic().equals(topic)).toList();
    if (reachable.isEmpty()) return;
    String payload = payloadJson(message);
    for (DeviceTarget target : reachable) {
      executor.execute(() -> post(target.token(), topic, target.topic() == null, payload));
    }
  }

  private void post(String token, String topic, boolean learnTopic, String payload) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(props.host() + "/3/device/" + token))
              .header("authorization", "bearer " + tokenProvider.token())
              .header("apns-topic", topic)
              .header("apns-push-type", "alert")
              .timeout(Duration.ofSeconds(10))
              .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 410
          || (response.statusCode() == 400 && response.body().contains("BadDeviceToken"))) {
        deviceTokens.deleteByToken(token);
      } else if (learnTopic && response.statusCode() < 300) {
        deviceTokens.updateTopic(token, topic);
      } else if (learnTopic
          && response.statusCode() == 400
          && response.body().contains("DeviceTokenNotForTopic")) {
        deviceTokens.updateTopic(token, props.otherTopic(topic));
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
   * 구버전 앱 호환을 위해 aps.alert/sound는 유지한다. type·shortCode는 최상위 키이며 값이 없으면 생략한다. shortCode가 있는 알림에만
   * LINK_STATS category를 붙인다.
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
    PushRoute route = message.route();
    if (route != null) {
      if (route.actorUsername() != null) root.put("actorUsername", route.actorUsername());
      if (route.ownerUsername() != null) root.put("ownerUsername", route.ownerUsername());
      if (route.postSlug() != null) root.put("postSlug", route.postSlug());
      if (route.seriesSlug() != null) root.put("seriesSlug", route.seriesSlug());
      if (route.collectionId() != null) root.put("collectionId", route.collectionId());
    }
    return jsonMapper.writeValueAsString(root);
  }

  private static String tail(String token) {
    return token.length() <= 6 ? token : token.substring(token.length() - 6);
  }
}
