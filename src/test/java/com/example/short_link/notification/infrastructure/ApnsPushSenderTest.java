package com.example.short_link.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.push.ApnsProperties;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서명 체인(.p8 파싱 → ES256 → DER→JOSE)의 회귀 가드 — 여기가 미묘하게 틀어지면 모든 푸시가 403/InvalidProviderToken 으로 조용히
 * 죽는다. 실 HTTP 발송은 다루지 않는다(샌드박스 게이트웨이는 테스트에서 닿지 않는 외부 세계).
 */
@ExtendWith(MockitoExtension.class)
class ApnsPushSenderTest {

  @Mock private DeviceTokenRepository deviceTokens;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  private static KeyPair p256() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    return generator.generateKeyPair();
  }

  private static String pem(KeyPair pair) {
    return "-----BEGIN PRIVATE KEY-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
            .encodeToString(pair.getPrivate().getEncoded())
        + "\n-----END PRIVATE KEY-----\n";
  }

  private ApnsPushSender sender(String privateKeyPem) {
    return new ApnsPushSender(
        new ApnsProperties("TEAM123456", "KEY1234567", null, privateKeyPem, false),
        deviceTokens,
        jsonMapper);
  }

  @Test
  void jwtIsEs256SignedAndVerifiableWithTheKey() throws Exception {
    KeyPair pair = p256();
    String jwt = sender(pem(pair)).jwt();

    String[] parts = jwt.split("\\.");
    assertThat(parts).hasSize(3);
    String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
    String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    assertThat(header).contains("\"alg\":\"ES256\"").contains("\"kid\":\"KEY1234567\"");
    assertThat(claims).contains("\"iss\":\"TEAM123456\"").contains("\"iat\":");

    byte[] jose = Base64.getUrlDecoder().decode(parts[2]);
    assertThat(jose).hasSize(64);
    Signature verifier = Signature.getInstance("SHA256withECDSA");
    verifier.initVerify(pair.getPublic());
    verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
    assertThat(verifier.verify(joseToDer(jose))).isTrue();
  }

  @Test
  void jwtIsCachedWithinTheWindow() throws Exception {
    ApnsPushSender sender = sender(pem(p256()));

    assertThat(sender.jwt()).isSameAs(sender.jwt());
  }

  @Test
  void unconfiguredSenderIsNoOp() {
    ApnsPushSender sender = sender(null);

    sender.send(1L, new PushSender.PushMessage("kurl", null, "본문"));
    sender.sendToAll(List.of(1L, 2L), new PushSender.PushMessage("kurl", null, "본문"));

    verifyNoInteractions(deviceTokens);
  }

  @Test
  void sendWithoutRegisteredDevicesDispatchesNothing() throws Exception {
    when(deviceTokens.tokensForUser(1L)).thenReturn(List.of());
    when(deviceTokens.tokensForUsers(List.of(1L, 2L))).thenReturn(List.of());

    ApnsPushSender sender = sender(pem(p256()));
    sender.send(1L, new PushSender.PushMessage("kurl", "제목", "본문"));
    sender.sendToAll(List.of(1L, 2L), new PushSender.PushMessage("kurl", "제목", "본문"));
    sender.sendToAll(List.of(), new PushSender.PushMessage("kurl", "제목", "본문"));
  }

  @Test
  void dispatchSerializesPayloadBeforeHandoff() throws Exception {
    // 페이로드 직렬화는 디스패치 시점(동기)에 한 번 — 발송 자체는 데몬 풀의 비동기 best-effort 라
    // 여기서는 큐잉까지만 본다(게이트웨이에 닿지 않는 토큰이므로 백그라운드 시도는 조용히 죽는다).
    when(deviceTokens.tokensForUser(1L)).thenReturn(List.of("dead-token"));

    ApnsPushSender sender = sender(pem(p256()));
    sender.send(1L, new PushSender.PushMessage("kurl", "제목 줄", "본문"));
    sender.send(1L, new PushSender.PushMessage("kurl", null, "부제 없는 본문"));
  }

  @Test
  void payloadCarriesRoutingKeysForLinkNotificationWithShortCode() {
    // shortCode 있는 링크 알림 — aps 형제 최상위 type·shortCode + category="LINK_STATS"(→ 앱의 "통계 보기" 액션).
    String payload =
        sender(null)
            .payloadJson(
                new PushSender.PushMessage(
                    "kurl", "/spring", "첫 클릭이 들어왔어요 🎉", "FIRST_CLICK", "spring"));

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
        sender(null)
            .payloadJson(new PushSender.PushMessage("kurl", "어제 요약", "어제 12 클릭", "DIGEST", null));

    JsonNode root = jsonMapper.readTree(payload);
    assertThat(root.get("type").asString()).isEqualTo("DIGEST");
    assertThat(root.has("shortCode")).isFalse();
    assertThat(root.get("aps").has("category")).isFalse();
  }

  @Test
  void payloadHasNoRoutingKeysForRoutinglessMessage() {
    // 블로그 벨 등 라우팅 없는 알림 — 페이로드는 예전과 동일(aps.alert/sound 만).
    String payload = sender(null).payloadJson(new PushSender.PushMessage("kurl", "글 제목", "좋아합니다"));

    JsonNode root = jsonMapper.readTree(payload);
    assertThat(root.has("type")).isFalse();
    assertThat(root.has("shortCode")).isFalse();
    assertThat(root.get("aps").has("category")).isFalse();
    assertThat(root.get("aps").get("alert").get("body").asString()).isEqualTo("좋아합니다");
  }

  @Test
  void derToJoseRightAlignsPaddedAndShortIntegers() {
    // r = 33바이트(부호 0x00 패딩), s = 31바이트(짧음) — 둘 다 32바이트 칸에 우측 정렬돼야 한다.
    byte[] r = new byte[33];
    r[0] = 0x00;
    Arrays.fill(r, 1, 33, (byte) 0x7A);
    byte[] s = new byte[31];
    Arrays.fill(s, (byte) 0x3C);

    byte[] der = new byte[4 + r.length + 2 + s.length];
    der[0] = 0x30;
    der[1] = (byte) (der.length - 2);
    der[2] = 0x02;
    der[3] = (byte) r.length;
    System.arraycopy(r, 0, der, 4, r.length);
    der[4 + r.length] = 0x02;
    der[5 + r.length] = (byte) s.length;
    System.arraycopy(s, 0, der, 6 + r.length, s.length);

    byte[] jose = ApnsPushSender.derToJose(der);

    assertThat(jose).hasSize(64);
    assertThat(Arrays.copyOfRange(jose, 0, 32)).containsOnly((byte) 0x7A);
    assertThat(jose[32]).isZero();
    assertThat(Arrays.copyOfRange(jose, 33, 64)).containsOnly((byte) 0x3C);
  }

  /** 검증용 역변환 — JOSE r·s 64바이트를 DER 시퀀스로 되감는다. */
  private static byte[] joseToDer(byte[] jose) {
    byte[] r = derInt(Arrays.copyOfRange(jose, 0, 32));
    byte[] s = derInt(Arrays.copyOfRange(jose, 32, 64));
    byte[] der = new byte[2 + r.length + s.length];
    der[0] = 0x30;
    der[1] = (byte) (r.length + s.length);
    System.arraycopy(r, 0, der, 2, r.length);
    System.arraycopy(s, 0, der, 2 + r.length, s.length);
    return der;
  }

  private static byte[] derInt(byte[] raw) {
    int start = 0;
    while (start < raw.length - 1 && raw[start] == 0) {
      start++;
    }
    byte[] value = Arrays.copyOfRange(raw, start, raw.length);
    boolean pad = (value[0] & 0x80) != 0;
    byte[] out = new byte[2 + value.length + (pad ? 1 : 0)];
    out[0] = 0x02;
    out[1] = (byte) (value.length + (pad ? 1 : 0));
    System.arraycopy(value, 0, out, pad ? 3 : 2, value.length);
    return out;
  }
}
