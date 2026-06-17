package com.example.short_link.notification.infrastructure;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.application.push.VapidProperties;
import com.example.short_link.user.domain.repository.WebPushSubscriptionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * VAPID 미설정이면 발송기는 조용한 no-op(키 없는 개발/CI 환경에서 앱이 평소처럼 돌도록). 설정돼 있고 구독이 없으면 저장소만 한 번 조회하고 끝(실제 네트워크
 * 발송은 외부 세계라 단위 테스트에서 다루지 않는다). 키는 테스트 전용 VAPID 페어 — 운영 키 아님.
 */
@ExtendWith(MockitoExtension.class)
class WebPushSenderTest {

  // 테스트 전용 VAPID 키페어(web-push generate-vapid-keys). PushService 생성이 성공하는지(=설정 경로 진입)만 확인.
  private static final String VAPID_PUB =
      "BDRYNSI6ZzbVloG_D7sPTu3lU2N21O9HYNpbOZcRE_ucL9jmRGQfka41izKHNatl4Ylm2A3FVD8BHUO_9dku_e0";
  private static final String VAPID_PRIV = "Xd6nNWdkpLd9nGreYejIzx95GTdWnMXOtaphVqvMVoA";

  @Mock private WebPushSubscriptionRepository subscriptions;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  private WebPushSender configured() {
    return new WebPushSender(
        new VapidProperties(VAPID_PUB, VAPID_PRIV, "mailto:test@kurl.me"),
        subscriptions,
        jsonMapper);
  }

  @Test
  void noOpWhenVapidUnconfigured() {
    WebPushSender sender =
        new WebPushSender(new VapidProperties(null, null, null), subscriptions, jsonMapper);
    PushSender.PushMessage message = new PushSender.PushMessage("kurl", "글 제목", "새 글");

    sender.send(1L, message);
    sender.sendToAll(List.of(1L, 2L), message);

    verifyNoInteractions(subscriptions);
  }

  @Test
  void configuredSendLooksUpRecipientSubscriptions() {
    when(subscriptions.findAllByUserId(1L)).thenReturn(List.of());

    configured().send(1L, new PushSender.PushMessage("kurl", "글 제목", "새 글"));

    verify(subscriptions).findAllByUserId(1L);
  }

  @Test
  void configuredSendToAllLooksUpAllRecipients() {
    when(subscriptions.findAllByUserIdIn(List.of(1L, 2L))).thenReturn(List.of());

    configured().sendToAll(List.of(1L, 2L), new PushSender.PushMessage("kurl", "글 제목", "새 글"));

    verify(subscriptions).findAllByUserIdIn(List.of(1L, 2L));
  }

  @Test
  void configuredSendToAllIgnoresEmptyRecipients() {
    configured().sendToAll(List.of(), new PushSender.PushMessage("kurl", "글 제목", "새 글"));

    verifyNoInteractions(subscriptions);
  }
}
