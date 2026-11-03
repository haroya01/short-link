package com.example.short_link.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.push.ApnsProperties;
import com.example.short_link.notification.application.push.PushActor;
import com.example.short_link.notification.application.push.PushApp;
import com.example.short_link.notification.application.push.PushRoute;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.application.push.PushSpeech;
import com.example.short_link.user.application.push.DevicePushTargets;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ApnsPushSenderTest {

  @Mock private DeviceTokenRepository deviceTokens;
  @Mock private ApnsTokenProvider tokenProvider;
  @Mock private HttpClient http;
  @Mock private HttpResponse<String> response;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final ApnsProperties props =
      new ApnsProperties("TEAM123456", "KEY1234567", null, null, false, null);

  private ApnsPushSender sender() {
    return new ApnsPushSender(
        props, new DevicePushTargets(deviceTokens), jsonMapper, tokenProvider, http, Runnable::run);
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
    when(deviceTokens.targetsForUser(eq(1L), any())).thenReturn(List.of());
    when(deviceTokens.targetsForUsers(eq(List.of(1L, 2L)), any())).thenReturn(List.of());

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
    when(deviceTokens.targetsForUser(eq(1L), any()))
        .thenReturn(
            List.of(new DeviceTarget("device-one", null), new DeviceTarget("device-two", null)));
    JsonMapper mapper = mock(JsonMapper.class);
    when(mapper.writeValueAsString(any())).thenReturn("encoded-payload");
    List<Runnable> pending = new ArrayList<>();
    ApnsPushSender sender =
        new ApnsPushSender(
            props, new DevicePushTargets(deviceTokens), mapper, tokenProvider, http, pending::add);

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
    when(deviceTokens.targetsForUser(eq(1L), any()))
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
    when(deviceTokens.targetsForUser(eq(1L), any()))
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
    when(deviceTokens.targetsForUser(eq(1L), any()))
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
                    new PushRoute("yuki", null, "my-post", null, null, null, null, null)));

    JsonNode root = jsonMapper.readTree(payload);
    assertThat(root.get("type").asString()).isEqualTo("LIKE");
    assertThat(root.get("actorUsername").asString()).isEqualTo("yuki");
    assertThat(root.get("postSlug").asString()).isEqualTo("my-post");
    assertThat(root.has("ownerUsername")).isFalse();
    assertThat(root.has("seriesSlug")).isFalse();
    assertThat(root.has("collectionId")).isFalse();
    assertThat(root.has("commentId")).isFalse();
    assertThat(root.has("highlightId")).isFalse();
    assertThat(root.get("aps").has("category")).isFalse();
  }

  @Test
  void payloadCarriesCommentAndHighlightIdsAsNumbers() {
    String comment =
        sender()
            .payloadJson(
                new PushSender.PushMessage(
                    "kurl",
                    "글 제목",
                    "yuki님이 댓글을 남겼습니다",
                    "COMMENT",
                    null,
                    PushApp.BLOG,
                    new PushRoute("yuki", "me", "my-post", null, null, 77L, null, null)));
    String highlight =
        sender()
            .payloadJson(
                new PushSender.PushMessage(
                    "kurl",
                    "글 제목",
                    "yuki님이 답글을 남겼습니다",
                    "REPLY",
                    null,
                    PushApp.BLOG,
                    new PushRoute("yuki", "me", "my-post", null, null, null, 41L, null)));

    JsonNode commentRoot = jsonMapper.readTree(comment);
    assertThat(commentRoot.get("commentId").isNumber()).isTrue();
    assertThat(commentRoot.get("commentId").asLong()).isEqualTo(77L);
    assertThat(commentRoot.has("highlightId")).isFalse();
    JsonNode highlightRoot = jsonMapper.readTree(highlight);
    assertThat(highlightRoot.get("highlightId").asLong()).isEqualTo(41L);
    assertThat(highlightRoot.has("commentId")).isFalse();
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
                    new PushRoute("yuki", null, null, null, 42L, null, null, null)));

    assertThat(jsonMapper.readTree(payload).get("collectionId").asLong()).isEqualTo(42L);
  }

  @Test
  void aPushWithoutAnActorKeepsTheShapeOlderAppsRead() {
    String blog =
        sender()
            .payloadJson(
                new PushSender.PushMessage(
                    "kurl",
                    "글 제목",
                    "yuki님이 글을 좋아합니다",
                    "LIKE",
                    null,
                    PushApp.BLOG,
                    PushRoute.toPost("yuki", "me", "my-post", null, null)));
    String link =
        sender()
            .payloadJson(
                new PushSender.PushMessage(
                    "kurl", "/spring", "첫 클릭", "FIRST_CLICK", "spring", PushApp.LINKS));

    assertThat(blog)
        .isEqualTo(
            "{\"aps\":{\"alert\":{\"title\":\"kurl\",\"subtitle\":\"글 제목\",\"body\":\"yuki님이 글을"
                + " 좋아합니다\"},\"sound\":\"default\"},\"type\":\"LIKE\",\"actorUsername\":\"yuki\","
                + "\"ownerUsername\":\"me\",\"postSlug\":\"my-post\"}");
    assertThat(link)
        .isEqualTo(
            "{\"aps\":{\"alert\":{\"title\":\"kurl\",\"subtitle\":\"/spring\",\"body\":\"첫 클릭\"},"
                + "\"sound\":\"default\",\"category\":\"LINK_STATS\"},\"type\":\"FIRST_CLICK\","
                + "\"shortCode\":\"spring\"}");
  }

  @Test
  void someoneAddressingTheRecipientIsSentAsAConversationWithTheirFace() {
    String payload =
        sender()
            .payloadJson(
                new PushSender.PushMessage(
                    "kurl",
                    "나도 그래",
                    "alice님이 노트에 답글을 남겼습니다",
                    "NOTE_REPLY",
                    null,
                    PushApp.BLOG,
                    PushRoute.toNote("alice", "alice", 815L),
                    new PushActor(
                        7L,
                        null,
                        "alice",
                        "앨리스",
                        "https://cdn.kurl.example/avatars/7/a.jpg",
                        new PushSpeech("나도 그래", "도쿄 모임")),
                    "room:3"));

    assertThat(payload)
        .isEqualTo(
            "{\"aps\":{\"alert\":{\"title\":\"kurl\",\"subtitle\":\"나도 그래\",\"body\":\"alice님이 노트에"
                + " 답글을 남겼습니다\"},\"sound\":\"default\",\"mutable-content\":1,"
                + "\"thread-id\":\"room:3\"},\"type\":\"NOTE_REPLY\",\"actorUsername\":\"alice\","
                + "\"ownerUsername\":\"alice\",\"noteId\":815,\"actor\":{\"id\":7,\"username\":\"alice\","
                + "\"displayName\":\"앨리스\",\"avatarUrl\":\"https://cdn.kurl.example/avatars/7/a.jpg\"},"
                + "\"communication\":true,\"text\":\"나도 그래\",\"context\":\"도쿄 모임\"}");
  }

  @Test
  void someoneWhoOnlyActedIsSentWithTheirFaceButNotAsAConversation() {
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
                    PushRoute.toPost("yuki", "me", "my-post", null, null),
                    new PushActor(2L, null, "yuki", null, null, null),
                    "post:10"));

    assertThat(payload)
        .isEqualTo(
            "{\"aps\":{\"alert\":{\"title\":\"kurl\",\"subtitle\":\"글 제목\",\"body\":\"yuki님이 글을"
                + " 좋아합니다\"},\"sound\":\"default\",\"mutable-content\":1,\"thread-id\":\"post:10\"},"
                + "\"type\":\"LIKE\",\"actorUsername\":\"yuki\",\"ownerUsername\":\"me\","
                + "\"postSlug\":\"my-post\",\"actor\":{\"id\":2,\"username\":\"yuki\"}}");
  }

  @Test
  void anAccountElsewhereIsSentByItsRemoteIdWithoutAnAvatar() {
    String payload =
        sender()
            .payloadJson(
                new PushSender.PushMessage(
                    "kurl",
                    "hi",
                    "ged@m.example님이 노트에서 회원님을 언급했습니다",
                    "NOTE_MENTION",
                    null,
                    PushApp.BLOG,
                    new PushRoute(null, "me", null, null, null, null, null, 5L),
                    new PushActor(
                        null, 42L, "ged@m.example", "Ged", null, new PushSpeech("hi", "노트에서 멘션")),
                    "note:5"));

    JsonNode root = jsonMapper.readTree(payload);
    assertThat(root.get("actor").toString())
        .isEqualTo("{\"remoteId\":42,\"username\":\"ged@m.example\",\"displayName\":\"Ged\"}");
    assertThat(root.get("communication").asBoolean()).isTrue();
    assertThat(root.get("text").asString()).isEqualTo("hi");
    assertThat(root.get("context").asString()).isEqualTo("노트에서 멘션");
    assertThat(root.has("actorUsername")).isFalse();
  }

  @Test
  void aNoticeNobodyCausedGroupsByItsPostWithoutWakingTheExtension() {
    String payload =
        sender()
            .payloadJson(
                new PushSender.PushMessage(
                    "kurl",
                    "글 제목",
                    "글에 첫 독자가 생겼습니다",
                    "POST_FIRST_READER",
                    null,
                    PushApp.BLOG,
                    PushRoute.toPost(null, "me", "my-post", null, null),
                    null,
                    "post:10"));

    JsonNode root = jsonMapper.readTree(payload);
    assertThat(root.get("aps").get("thread-id").asString()).isEqualTo("post:10");
    assertThat(root.get("aps").has("mutable-content")).isFalse();
    assertThat(root.has("actor")).isFalse();
    assertThat(root.has("communication")).isFalse();
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
    when(deviceTokens.targetsForUser(eq(1L), any()))
        .thenReturn(List.of(new DeviceTarget("links-device", "focustime.kurl.links")));

    sender().send(1L, linkMessage());

    assertThat(sentTopic()).isEqualTo("focustime.kurl.links");
    verify(deviceTokens, never()).updateTopic(any(), any());
  }

  @Test
  void devicesOfTheOtherAppAreSkipped() {
    when(tokenProvider.configured()).thenReturn(true);
    when(deviceTokens.targetsForUser(eq(1L), any()))
        .thenReturn(List.of(new DeviceTarget("blog-device", "focustime.kurl")));

    sender().send(1L, linkMessage());

    verifyNoInteractions(http);
  }

  @Test
  void everyDeliveryOutcomeIsLoggedWithItsReason(CapturedOutput output) throws Exception {
    respond(400, "{\"reason\":\"TopicDisallowed\"}");
    when(deviceTokens.targetsForUser(eq(1L), any()))
        .thenReturn(List.of(new DeviceTarget("device-abcdef", "focustime.kurl.links")));

    sender().send(1L, linkMessage());

    assertThat(output)
        .contains(
            "push apns app=LINKS type=FIRST_CLICK outcome=rejected status=400 token=…abcdef"
                + " reason={\"reason\":\"TopicDisallowed\"}");
  }

  @Test
  void recipientWithoutDeviceIsLogged(CapturedOutput output) {
    when(tokenProvider.configured()).thenReturn(true);
    when(deviceTokens.targetsForUser(eq(1L), any())).thenReturn(List.of());

    sender().send(1L, linkMessage());

    assertThat(output).contains("push apns app=LINKS type=FIRST_CLICK outcome=no_device devices=0");
  }

  @Test
  void legacyDeviceLearnsItsTopicOnSuccess() throws Exception {
    respond(200, "");
    when(deviceTokens.targetsForUser(eq(1L), any()))
        .thenReturn(List.of(new DeviceTarget("old-device", null)));

    sender().send(1L, linkMessage());

    assertThat(sentTopic()).isEqualTo("focustime.kurl.links");
    verify(deviceTokens).updateTopic("old-device", "focustime.kurl.links");
    verify(deviceTokens, never()).deleteByToken(any());
  }

  @Test
  void legacyDeviceOfTheOtherAppIsRecordedAsThatApp() throws Exception {
    respond(400, "{\"reason\":\"DeviceTokenNotForTopic\"}");
    when(deviceTokens.targetsForUser(eq(1L), any()))
        .thenReturn(List.of(new DeviceTarget("old-device", null)));

    sender().send(1L, new PushSender.PushMessage("kurl", "글 제목", "좋아합니다"));

    assertThat(sentTopic()).isEqualTo("focustime.kurl");
    verify(deviceTokens).updateTopic("old-device", "focustime.kurl.links");
    verify(deviceTokens, never()).deleteByToken(any());
  }
}
