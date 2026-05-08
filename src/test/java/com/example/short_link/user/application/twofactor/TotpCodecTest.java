package com.example.short_link.user.application.twofactor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TotpCodecTest {

  @Test
  void generateAndVerifyCurrentCode() {
    String secret = TotpCodec.generateBase32Secret();
    long now = 1_700_000_000L;
    String code = TotpCodec.generateCode(secret, now / TotpCodec.PERIOD_SECONDS);
    assertThat(code).hasSize(TotpCodec.CODE_DIGITS).containsOnlyDigits();
    assertThat(TotpCodec.verify(secret, code, now)).isTrue();
  }

  @Test
  void verifyToleratesOneStepDrift() {
    String secret = TotpCodec.generateBase32Secret();
    long now = 1_700_000_000L;
    long step = now / TotpCodec.PERIOD_SECONDS;
    String prevCode = TotpCodec.generateCode(secret, step - 1);
    String nextCode = TotpCodec.generateCode(secret, step + 1);
    assertThat(TotpCodec.verify(secret, prevCode, now)).isTrue();
    assertThat(TotpCodec.verify(secret, nextCode, now)).isTrue();
  }

  @Test
  void rejectsCodeFromTwoStepsAway() {
    String secret = TotpCodec.generateBase32Secret();
    long now = 1_700_000_000L;
    long step = now / TotpCodec.PERIOD_SECONDS;
    String farCode = TotpCodec.generateCode(secret, step - 5);
    assertThat(TotpCodec.verify(secret, farCode, now)).isFalse();
  }

  @Test
  void rejectsMalformedCode() {
    String secret = TotpCodec.generateBase32Secret();
    assertThat(TotpCodec.verify(secret, "12345", 1_700_000_000L)).isFalse();
    assertThat(TotpCodec.verify(secret, "1234567", 1_700_000_000L)).isFalse();
    assertThat(TotpCodec.verify(secret, null, 1_700_000_000L)).isFalse();
  }

  @Test
  void provisioningUriHasCorrectFormat() {
    String uri = TotpCodec.provisioningUri("kurl.me", "user@example.com", "JBSWY3DPEHPK3PXP");
    assertThat(uri).startsWith("otpauth://totp/kurl.me:user%40example.com?");
    assertThat(uri).contains("secret=JBSWY3DPEHPK3PXP");
    assertThat(uri).contains("issuer=kurl.me");
    assertThat(uri).contains("algorithm=SHA1");
    assertThat(uri).contains("digits=6");
    assertThat(uri).contains("period=30");
  }

  @Test
  void rfc6238ReferenceVector() {
    // RFC 6238 Appendix B test vectors use HMAC-SHA1 with secret "12345678901234567890"
    // (ASCII), which is base32 "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    // T=59 → step = 1 → expected 94287082
    assertThat(TotpCodec.generateCode(secret, 1L)).isEqualTo("287082");
    // T=1111111109 → step = 37037036 → expected 07081804
    assertThat(TotpCodec.generateCode(secret, 37037036L)).isEqualTo("081804");
  }
}
