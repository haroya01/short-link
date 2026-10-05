package com.example.short_link.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.push.PushApp;
import com.example.short_link.notification.application.push.PushRoute;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.application.push.VapidProperties;
import com.example.short_link.user.domain.repository.WebPushSubscriptionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 키는 테스트 전용 VAPID 페어이고 운영 키가 아니다. 실제 네트워크 발송은 단위 테스트에서 다루지 않는다.
@ExtendWith(MockitoExtension.class)
class WebPushSenderTest {

  // 테스트 전용 VAPID 키페어(web-push generate-vapid-keys). PushService 생성이 성공하는지(=설정 경로 진입)만 확인.
  private static final String VAPID_PUB =
      "BDRYNSI6ZzbVloG_D7sPTu3lU2N21O9HYNpbOZcRE_ucL9jmRGQfka41izKHNatl4Ylm2A3FVD8BHUO_9dku_e0";
  private static final String VAPID_PRIV = "Xd6nNWdkpLd9nGreYejIzx95GTdWnMXOtaphVqvMVoA";

  private static final String BLOG = "https://blog.kurl.me";

  @Mock private WebPushSubscriptionRepository subscriptions;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  private WebPushSender configured() {
    return new WebPushSender(
        new VapidProperties(VAPID_PUB, VAPID_PRIV, "mailto:test@kurl.me"),
        subscriptions,
        jsonMapper,
        BLOG);
  }

  @Test
  void noOpWhenVapidUnconfigured() {
    WebPushSender sender =
        new WebPushSender(new VapidProperties(null, null, null), subscriptions, jsonMapper, BLOG);
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

  @Test
  void linkAppMessagesNeverReachWebSubscriptions() {
    PushSender.PushMessage links =
        new PushSender.PushMessage(
            "kurl", "/spring", "첫 클릭", "FIRST_CLICK", "spring", PushApp.LINKS);

    configured().send(1L, links);
    configured().sendToAll(List.of(1L, 2L), links);

    verifyNoInteractions(subscriptions);
  }

  @Test
  void payloadCarriesRoutingHintsForLinkNotification() {
    JsonNode root =
        jsonMapper.readTree(
            configured()
                .payload(
                    new PushSender.PushMessage(
                        "kurl", "/spring", "첫 클릭", "FIRST_CLICK", "spring", PushApp.LINKS)));

    assertThat(root.get("type").asString()).isEqualTo("FIRST_CLICK");
    assertThat(root.get("shortCode").asString()).isEqualTo("spring");
    assertThat(root.get("title").asString()).isEqualTo("첫 클릭");
    assertThat(root.get("body").asString()).isEqualTo("/spring");
  }

  @Test
  void payloadOmitsRoutingHintsForRoutinglessMessage() {
    JsonNode root =
        jsonMapper.readTree(
            configured().payload(new PushSender.PushMessage("kurl", "글 제목", "새 글")));

    assertThat(root.has("type")).isFalse();
    assertThat(root.has("shortCode")).isFalse();
    assertThat(root.get("title").asString()).isEqualTo("새 글");
  }

  @Test
  void blogPushOpensTheSpotItPointsAt() {
    assertThat(urlOf(new PushRoute("yuki", "me", "my-post", null, null, 77L, null)))
        .isEqualTo("https://blog.kurl.me/@me/my-post#comment-77");
    assertThat(urlOf(new PushRoute("yuki", "mika", "their-post", null, null, null, 41L)))
        .isEqualTo("https://blog.kurl.me/@mika/their-post?highlightId=41&thread=1");
    assertThat(urlOf(new PushRoute("yuki", "yuki", "fresh", null, null, null, null)))
        .isEqualTo("https://blog.kurl.me/@yuki/fresh");
  }

  @Test
  void blogPushOpensSeriesCollectionAndProfile() {
    assertThat(urlOf(new PushRoute("yuki", "me", null, "tokyo-walks", null, null, null)))
        .isEqualTo("https://blog.kurl.me/@me/series/tokyo-walks");
    assertThat(urlOf(new PushRoute("yuki", null, null, null, 42L, null, null)))
        .isEqualTo("https://blog.kurl.me/collections/42");
    assertThat(urlOf(new PushRoute("stranger99", null, null, null, null, null, null)))
        .isEqualTo("https://blog.kurl.me/@stranger99");
  }

  @Test
  void blogPushEncodesPathSegmentsAndFallsBackHomeWithoutRoute() {
    assertThat(urlOf(new PushRoute("yuki", "me", "도쿄 산책", null, null, null, null)))
        .isEqualTo("https://blog.kurl.me/@me/%EB%8F%84%EC%BF%84%20%EC%82%B0%EC%B1%85");
    assertThat(urlOf(new PushRoute(null, null, null, null, null, null, null))).isEqualTo("/");
    assertThat(
            jsonMapper
                .readTree(configured().payload(new PushSender.PushMessage("kurl", "글", "새 글")))
                .get("url")
                .asString())
        .isEqualTo("/");
  }

  private String urlOf(PushRoute route) {
    return jsonMapper
        .readTree(
            configured()
                .payload(
                    new PushSender.PushMessage(
                        "kurl", "글 제목", "본문", "COMMENT", null, PushApp.BLOG, route)))
        .get("url")
        .asString();
  }
}
