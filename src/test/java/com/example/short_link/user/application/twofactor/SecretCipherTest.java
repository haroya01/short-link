package com.example.short_link.user.application.twofactor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

  @Test
  void plainModeRoundTripsPrefixed() {
    SecretCipher cipher = new SecretCipher("");
    String stored = cipher.encrypt("JBSWY3DPEHPK3PXP");
    assertThat(stored).startsWith("plain:");
    assertThat(cipher.decrypt(stored)).isEqualTo("JBSWY3DPEHPK3PXP");
  }

  @Test
  void aesModeRoundTrips() {
    byte[] key = new byte[32];
    new java.security.SecureRandom().nextBytes(key);
    SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(key));
    String stored = cipher.encrypt("JBSWY3DPEHPK3PXP");
    assertThat(stored).startsWith("v1:");
    assertThat(cipher.decrypt(stored)).isEqualTo("JBSWY3DPEHPK3PXP");
  }

  @Test
  void unprefixedLegacyRowReturnedAsIs() {
    SecretCipher cipher = new SecretCipher("");
    assertThat(cipher.decrypt("JBSWY3DPEHPK3PXP")).isEqualTo("JBSWY3DPEHPK3PXP");
  }

  @Test
  void aesProducesDistinctCiphertextEachCall() {
    byte[] key = new byte[32];
    new java.security.SecureRandom().nextBytes(key);
    SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(key));
    String a = cipher.encrypt("hello");
    String b = cipher.encrypt("hello");
    assertThat(a).isNotEqualTo(b);
    assertThat(cipher.decrypt(a)).isEqualTo("hello");
    assertThat(cipher.decrypt(b)).isEqualTo("hello");
  }
}
