package com.example.short_link.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.push.ApnsProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ApnsTokenProviderTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

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

  private static ApnsProperties properties(String privateKeyPem) {
    return new ApnsProperties("TEAM123456", "KEY1234567", null, privateKeyPem, false);
  }

  @Test
  void jwtIsEs256SignedAndVerifiableWithTheKey() throws Exception {
    KeyPair pair = p256();
    ApnsTokenProvider provider = new ApnsTokenProvider(properties(pem(pair)), FIXED_CLOCK);
    assertThat(provider.configured()).isTrue();
    String jwt = provider.token();

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
  void cachedTokenExpiresAtExactlyFiftyMinutes() throws Exception {
    Clock clock = mock(Clock.class);
    Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
    when(clock.instant()).thenReturn(issuedAt);
    ApnsTokenProvider provider = new ApnsTokenProvider(properties(pem(p256())), clock);
    String original = provider.token();

    Instant expiresAt = issuedAt.plus(Duration.ofMinutes(50));
    when(clock.instant()).thenReturn(expiresAt.minusNanos(1));
    assertThat(provider.token()).isSameAs(original);

    when(clock.instant()).thenReturn(expiresAt);
    String renewed = provider.token();
    assertThat(renewed).isNotEqualTo(original);
    String claims =
        new String(Base64.getUrlDecoder().decode(renewed.split("\\.")[1]), StandardCharsets.UTF_8);
    assertThat(JsonMapper.builder().build().readTree(claims).path("iat").asLong())
        .isEqualTo(expiresAt.getEpochSecond());
    assertThat(provider.token()).isSameAs(renewed);
  }

  @Test
  void missingCredentialsDisableTokenCreationWithoutParsingTheKey() {
    assertThat(new ApnsTokenProvider(properties(null), FIXED_CLOCK).configured()).isFalse();
    assertThat(
            new ApnsTokenProvider(
                    new ApnsProperties("", "KEY1234567", null, "not-a-key", false), FIXED_CLOCK)
                .configured())
        .isFalse();
  }

  @Test
  void configuredButMalformedPemFailsDuringInitialization() {
    assertThatThrownBy(() -> new ApnsTokenProvider(properties("not-a-key"), FIXED_CLOCK))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("APNs private key (.p8 PEM) is malformed");
  }

  @Test
  void derToJoseRightAlignsPaddedAndShortIntegers() {
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

    byte[] jose = ApnsTokenProvider.derToJose(der);

    assertThat(jose).hasSize(64);
    assertThat(Arrays.copyOfRange(jose, 0, 32)).containsOnly((byte) 0x7A);
    assertThat(jose[32]).isZero();
    assertThat(Arrays.copyOfRange(jose, 33, 64)).containsOnly((byte) 0x3C);
  }

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
