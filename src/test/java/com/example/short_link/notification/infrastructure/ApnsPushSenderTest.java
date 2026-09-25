package com.example.short_link.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.push.ApnsProperties;
import com.example.short_link.notification.application.push.PushApp;
import com.example.short_link.notification.application.push.PushRoute;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.user.domain.DeviceTarget;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ApnsPushSenderTest {

  @Mock private DeviceTokenRepository deviceTokens;
  @Mock private ApnsTokenProvider tokenProvider;
  @Mock private HttpClient http;
  @Mock private HttpResponse<String> response;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final ApnsProperties props =
      new ApnsProperties("TEAM123456", "KEY1234567", null, null, false, null);

  private ApnsPushSender sender() {
    return new ApnsPushSender(props, deviceTokens, jsonMapper, tokenProvider, http, Runnable::run);
  }

  @Test
  void unconfiguredSenderIsNoOp() {
    ApnsPushSender sender = sender();

    sender.send(1L, new PushSender.PushMessage("kurl", null, "본문"));
    sender.sendToAll(List.of(1L, 2L), new PushSender.PushMessage("kurl", null, "본문"));

    verifyNoInteractions(deviceTokens, http);
    verify(tokenProvider, never()).token();
  }

  @Test
  void sendWithoutRegisteredDevicesDispatchesNothing() {
    when(tokenProvider.configured()).thenReturn(true);
    when(deviceTokens.targetsForUser(1L)).thenReturn(List.of());
    when(deviceTokens.targetsForUsers(List.of(1L, 2L))).thenReturn(List.of());

    ApnsPushSender sender = sender();
    sender.send(1L, new PushSender.PushMessage("kurl", "제목", "본문"));
    sender.sendToAll(List.of(1L, 2L), new PushSender.PushMessage("kurl", "제목", "본문"));
    sender.sendToAll(List.of(), new PushSender.PushMessage("kurl", "제목", "본문"));

    verifyNoInteractions(http);
    verify(tokenProvider, never()).token();
  }

  @Test
  void dispatchSerializesOnePayloadBeforeQueuingEachDevice() {
    when(tokenProvider.configured()).thenReturn(true);
    when(deviceTokens.targetsForUser(1L))
        .thenReturn(
            List.of(new DeviceTarget("device-one", null), new DeviceTarget("device-two", null)));
    JsonMapper mapper = mock(JsonMapper.class);
    when(mapper.writeValueAsString(any())).thenReturn("encoded-payload");
    List<Runnable> pending = new ArrayList<>();
    ApnsPushSender sender =
        new ApnsPushSender(props, deviceTokens, mapper, tokenProvider, http, pending::add);

    sender.send(1L, new PushSender.PushMessage("kurl", "제목 줄", "본문"));

    assertThat(pending).hasSize(2);
    verify(mapper).writeValueAsString(any());
    verifyNoInteractions(http);
    verify(tokenProvider, never()).token();
  }

  @ParameterizedTest
  @CsvSource({
    "200, accepted, 0",
    "410, Unregistered, 1",
    "400, BadDeviceToken, 1",
    "400, DeviceTokenNotForTopic, 0",
    "500, BadDeviceToken, 0"
  })
  void sendsProviderCredentialsAndDiscardsOnlyInvalidDevices(
      int status, String reason, int expectedDeletions) throws Exception {
    when(tokenProvider.configured()).thenReturn(true);
    when(tokenProvider.token()).thenReturn("provider-token");
    when(deviceTokens.targetsForUser(1L))
        .thenReturn(List.of(new DeviceTarget("device-one", "focustime.kurl")));
    when(response.statusCode()).thenReturn(status);
    if (status >= 400 && status != 410) when(response.body()).thenReturn(reason);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);

    sender().send(1L, new PushSender.PushMessage("kurl", "제목", "본문"));

    ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
    assertThat(request.getValue().uri().toString())
        .isEqualTo("https://api.sandbox.push.apple.com/3/device/device-one");
    assertThat(request.getValue().method()).isEqualTo("POST");
    assertThat(request.getValue().headers().firstValue("authorization"))
        .contains("bearer provider-token");
    assertThat(request.getValue().headers().firstValue("apns-topic")).contains("focustime.kurl");
    assertThat(request.getValue().headers().firstValue("apns-push-type")).contains("alert");
    assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(10));
    verify(tokenProvider).token();
    verify(deviceTokens, times(expectedDeletions)).deleteByToken("device-one");
    verifyNoMoreInteractions(http);
  }

  @Test
  void transportFailureDoesNotRetryOrDiscardTheDevice() throws Exception {
    when(tokenProvider.configured()).thenReturn(true);
    when(tokenProvider.token()).thenReturn("provider-token");
    when(deviceTokens.targetsForUser(1L))
        .thenReturn(List.of(new DeviceTarget("device-one", "focustime.kurl")));
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("offline"));

    sender().send(1L, new PushSender.PushMessage("kurl", null, "본문"));

    verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    verifyNoMoreInteractions(http);
    verify(deviceTokens, never()).deleteByToken(any());
  }

  @Test
  void interruptedDeliveryRestoresTheThreadInterruptFlag() throws Exception {
    when(tokenProvider.configured()).thenReturn(true);
    when(tokenProvider.token()).thenReturn("provider-token");
    when(deviceTokens.targetsForUser(1L))
        .thenReturn(List.of(new DeviceTarget("device-one", "focustime.kurl")));
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new InterruptedException("cancelled"));

    try {
      sender().send(1L, new PushSender.PushMessage("kurl", null, "본문"));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      verify(deviceTokens, never()).deleteByToken(any());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void payloadCarriesRoutingKeysForLinkNotificationWithShortCode() {
    // shortCode 있는 링크 알림 — aps 형제 최상위 type·shortCode + category="LINK_STATS"(→ 앱의 "통계 보기" 액션).
    String payload =
        sender()
            .payloadJson(
                new PushSender.PushMessage(
                    "kurl", "/spring", "첫 클릭이 들어왔어요 🎉", "FIRST_CLICK", "spring", PushApp.LINKS));

    JsonNode root = jsonMapper.readTree(payload);
    assertThat(root.get("type").asString()).isEqualTo("FIRST_CLICK");
    assertThat(root.get("shortCode").asString()).isEqualTo("spring");
    assertThat(root.get("aps").get("category").asString()).isEqualTo("LINK_STATS");
    // 기존 alert/sound 구조는 불변(구버전 앱 호환).
    JsonNode alert = root.get("aps").get("alert");
    assertThat(alert.get("title").asString()).isEqualTo("kurl");
    assertThat(alert.get("subtitle").asString()).isEqualTo("/spring");
    assertThat(alert.get("body").asString()).isEqualTo("첫 클릭이 들어왔어요 🎉");
    assertThat(root.get("aps").get("sound").asString()).isEqualTo("default");
  }

  @Test
  void payloadOmitsShortCodeAndCategoryForCodelessType() {
    // 다이제스트처럼 링크 단위가 아닌 알림 — type 은 싣되 shortCode·category 는 생략.
    String payload =
        sender()
            .payloadJson(
                new PushSender.PushMessage(
                    "kurl", "어제 요약", "어제 12 클릭", "DIGEST", null, PushApp.LINKS));

    JsonNode root = jsonMapper.readTree(payload);
    assertThat(root.get("type").asString()).isEqualTo("DIGEST");
    assertThat(root.has("shortCode")).isFalse();
    assertThat(root.get("aps").has("category")).isFalse();
  }

  @Test
  void payloadHasNoRoutingKeysForRoutinglessMessage() {
    // 블로그 벨 등 라우팅 없는 알림 — 페이로드는 예전과 동일(aps.alert/sound 만).
    String payload = sender().payloadJson(new PushSender.PushMessage("kurl", "글 제목", "좋아합니다"));

    JsonNode root = jsonMapper.readTree(payload);
    assertThat(root.has("type")).isFalse();
    assertThat(root.has("shortCode")).isFalse();
    assertThat(root.get("aps").has("category")).isFalse();
    assertThat(root.get("aps").get("alert").get("body").asString()).isEqualTo("좋아합니다");
  }

  @Test
  void payloadCarriesBlogRouteKeys() {
    String payload =
        sender()
            .payloadJson(
                new PushSender.PushMessage(
                    "kurl",
                    "글 제목",
                    "yuki님이 글을 좋아합니다",
                    "LIKE",
                    null,
                    PushApp.BLOG,
                    new PushRoute("yuki", null, "my-post", null, null)));

    JsonNode root = jsonMapper.readTree(payload);
    assertThat(root.get("type").asString()).isEqualTo("LIKE");
    assertThat(root.get("actorUsername").asString()).isEqualTo("yuki");
    assertThat(root.get("postSlug").asString()).isEqualTo("my-post");
    assertThat(root.has("ownerUsername")).isFalse();
    assertThat(root.has("seriesSlug")).isFalse();
    assertThat(root.has("collectionId")).isFalse();
    assertThat(root.get("aps").has("category")).isFalse();
  }

  @Test
  void payloadCarriesCollectionIdAsNumber() {
    String payload =
        sender()
            .payloadJson(
                new PushSender.PushMessage(
                    "kurl",
                    "도쿄 산책",
                    "yuki님이 회원님의 글을 컬렉션에 엮었습니다",
                    "CONNECTED",
                    null,
                    PushApp.BLOG,
                    new PushRoute("yuki", null, null, null, 42L)));

    assertThat(jsonMapper.readTree(payload).get("collectionId").asLong()).isEqualTo(42L);
  }

  private void respond(int status, String body) throws Exception {
    when(tokenProvider.configured()).thenReturn(true);
    when(tokenProvider.token()).thenReturn("provider-token");
    when(response.statusCode()).thenReturn(status);
    if (status >= 400) when(response.body()).thenReturn(body);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
  }

  private String sentTopic() throws Exception {
    ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
    return request.getValue().headers().firstValue("apns-topic").orElseThrow();
  }

  private static PushSender.PushMessage linkMessage() {
    return new PushSender.PushMessage(
        "kurl", "/spring", "첫 클릭", "FIRST_CLICK", "spring", PushApp.LINKS);
  }

  @Test
  void linkNotificationsGoToTheLinksAppTopic() throws Exception {
    respond(200, "");
    when(deviceTokens.targetsForUser(1L))
        .thenReturn(List.of(new DeviceTarget("links-device", "focustime.kurl.links")));

    sender().send(1L, linkMessage());

    assertThat(sentTopic()).isEqualTo("focustime.kurl.links");
    verify(deviceTokens, never()).updateTopic(any(), any());
  }

  @Test
  void devicesOfTheOtherAppAreSkipped() {
    when(tokenProvider.configured()).thenReturn(true);
    when(deviceTokens.targetsForUser(1L))
        .thenReturn(List.of(new DeviceTarget("blog-device", "focustime.kurl")));

    sender().send(1L, linkMessage());

    verifyNoInteractions(http);
  }

  @Test
  void legacyDeviceLearnsItsTopicOnSuccess() throws Exception {
    respond(200, "");
    when(deviceTokens.targetsForUser(1L)).thenReturn(List.of(new DeviceTarget("old-device", null)));

    sender().send(1L, linkMessage());

    assertThat(sentTopic()).isEqualTo("focustime.kurl.links");
    verify(deviceTokens).updateTopic("old-device", "focustime.kurl.links");
    verify(deviceTokens, never()).deleteByToken(any());
  }

  @Test
  void legacyDeviceOfTheOtherAppIsRecordedAsThatApp() throws Exception {
    respond(400, "{\"reason\":\"DeviceTokenNotForTopic\"}");
    when(deviceTokens.targetsForUser(1L)).thenReturn(List.of(new DeviceTarget("old-device", null)));

    sender().send(1L, new PushSender.PushMessage("kurl", "글 제목", "좋아합니다"));

    assertThat(sentTopic()).isEqualTo("focustime.kurl");
    verify(deviceTokens).updateTopic("old-device", "focustime.kurl.links");
    verify(deviceTokens, never()).deleteByToken(any());
  }
}
