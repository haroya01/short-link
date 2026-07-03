package com.example.short_link.notification.infrastructure;

import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.application.push.VapidProperties;
import com.example.short_link.user.domain.WebPushSubscriptionEntity;
import com.example.short_link.user.domain.repository.WebPushSubscriptionRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 브라우저 웹푸시 발송(VAPID) — 인앱 알림과 같은 내용을 데스크톱/안드로이드 브라우저로. APNs 발송기와 같은 규약: VAPID 키 미설정이면 조용한 no-op,
 * 발송은 작은 전용 풀에서 fire-and-forget(알림 저장 트랜잭션을 절대 붙잡지 않음 — 인앱 벨이 진실의 원천). 404/410(만료 구독)은 그 자리에서 폐기.
 */
@Component
@Slf4j
public class WebPushSender implements PushSender {

  private final WebPushSubscriptionRepository subscriptions;
  private final JsonMapper jsonMapper;
  private final ExecutorService executor;
  private final PushService pushService;

  public WebPushSender(
      VapidProperties props, WebPushSubscriptionRepository subscriptions, JsonMapper jsonMapper) {
    this.subscriptions = subscriptions;
    this.jsonMapper = jsonMapper;
    this.executor = Executors.newFixedThreadPool(2);
    // VAPID key parsing / ECDH needs the BouncyCastle provider registered (web-push doesn't do it
    // itself). Gate on whether keys exist — unset (or invalid) = no-op, like the APNs sender.
    PushService service = null;
    if (props.configured()) {
      if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
        Security.addProvider(new BouncyCastleProvider());
      }
      try {
        service = new PushService(props.publicKey(), props.privateKey(), props.subject());
      } catch (GeneralSecurityException e) {
        log.warn("VAPID keys present but invalid — web push disabled: {}", e.toString());
      }
    }
    this.pushService = service;
  }

  @Override
  public void send(Long recipientUserId, PushMessage message) {
    if (pushService == null) {
      return;
    }
    dispatch(subscriptions.findAllByUserId(recipientUserId), message);
  }

  @Override
  public void sendToAll(Collection<Long> recipientUserIds, PushMessage message) {
    if (pushService == null || recipientUserIds.isEmpty()) {
      return;
    }
    dispatch(subscriptions.findAllByUserIdIn(recipientUserIds), message);
  }

  private void dispatch(List<WebPushSubscriptionEntity> targets, PushMessage message) {
    if (targets.isEmpty()) {
      return;
    }
    byte[] payload = payload(message);
    for (WebPushSubscriptionEntity sub : targets) {
      executor.submit(() -> deliver(sub, payload));
    }
  }

  private void deliver(WebPushSubscriptionEntity sub, byte[] payload) {
    try {
      Notification notification =
          new Notification(sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
      HttpResponse response = pushService.send(notification);
      int status = response.getStatusLine().getStatusCode();
      if (status == 404 || status == 410) {
        // Subscription gone (browser unsubscribed / expired) — drop it so we stop trying.
        subscriptions.deleteByEndpoint(sub.getEndpoint());
      }
    } catch (Exception e) {
      // Push is a best-effort side channel; the in-app bell is the source of truth. Log, never
      // throw.
      log.warn("web push send failed for endpoint {}: {}", sub.getEndpoint(), e.toString());
    }
  }

  /**
   * Service Worker 가 읽는 페이로드 — 제목(행위)·본문(글 제목)·열 url, 그리고 APNs 와 같은 라우팅 힌트(type·shortCode). 값이 없는
   * 힌트 키는 생략한다(구독 SW 는 모르는 키를 무시하므로 추가만 해도 안전).
   */
  byte[] payload(PushMessage message) {
    String title = message.body();
    String body = message.subtitle() == null ? "" : message.subtitle();
    return jsonMapper.writeValueAsBytes(
        new WebPushPayload(title, body, "/", message.type(), message.shortCode()));
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record WebPushPayload(
      String title, String body, String url, String type, String shortCode) {}
}
