package com.example.short_link.notification.infrastructure;

import com.example.short_link.notification.application.push.PushApp;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.application.push.VapidProperties;
import com.example.short_link.user.domain.WebPushSubscriptionEntity;
import com.example.short_link.user.domain.repository.WebPushSubscriptionRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.net.URI;
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

/** VAPID 미설정이면 발송하지 않는다. 전송은 전용 풀에서 실행해 알림 저장 트랜잭션을 붙잡지 않으며, 404/410 응답은 구독을 폐기한다. */
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
    // web-push does not register the BouncyCastle provider required for VAPID parsing and ECDH.
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
    if (pushService == null || message.app() != PushApp.BLOG) {
      return;
    }
    dispatch(subscriptions.findAllByUserId(recipientUserId), message);
  }

  @Override
  public void sendToAll(Collection<Long> recipientUserIds, PushMessage message) {
    if (pushService == null || message.app() != PushApp.BLOG || recipientUserIds.isEmpty()) {
      return;
    }
    dispatch(subscriptions.findAllByUserIdIn(recipientUserIds), message);
  }

  private void dispatch(List<WebPushSubscriptionEntity> targets, PushMessage message) {
    if (targets.isEmpty()) {
      log.info("push web type={} outcome=no_subscription", message.type());
      return;
    }
    byte[] payload = payload(message);
    for (WebPushSubscriptionEntity sub : targets) {
      executor.submit(() -> deliver(sub, payload, message));
    }
  }

  private void deliver(WebPushSubscriptionEntity sub, byte[] payload, PushMessage message) {
    try {
      Notification notification =
          new Notification(sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
      HttpResponse response = pushService.send(notification);
      int status = response.getStatusLine().getStatusCode();
      String outcome = status < 300 ? "sent" : "rejected";
      if (status == 404 || status == 410) {
        subscriptions.deleteByEndpoint(sub.getEndpoint());
        outcome = "gone";
      }
      log.info(
          "push web type={} outcome={} status={} host={}",
          message.type(),
          outcome,
          status,
          host(sub.getEndpoint()));
    } catch (Exception e) {
      // Push is best-effort; delivery failure must not invalidate the in-app notification.
      log.warn(
          "push web type={} outcome=error host={}: {}",
          message.type(),
          host(sub.getEndpoint()),
          e.toString());
    }
  }

  private static String host(String endpoint) {
    try {
      return URI.create(endpoint).getHost();
    } catch (IllegalArgumentException e) {
      return "?";
    }
  }

  /** Service Worker 호환을 위해 type·shortCode는 값이 있을 때만 추가한다. 기존 Worker는 모르는 키를 무시한다. */
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
