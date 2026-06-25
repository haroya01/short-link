package com.example.short_link.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

class ProdConfigGuardTest {

  private static ProdConfigGuard guard(
      boolean prod,
      String jwtPriv,
      String jwtPub,
      String twofa,
      boolean pow,
      boolean safeBrowsing,
      String google) {
    Environment env = mock(Environment.class);
    when(env.matchesProfiles("prod")).thenReturn(prod);
    ProdConfigGuard g = new ProdConfigGuard(env);
    ReflectionTestUtils.setField(g, "jwtPrivateKey", jwtPriv);
    ReflectionTestUtils.setField(g, "jwtPublicKey", jwtPub);
    ReflectionTestUtils.setField(g, "twofaAesKey", twofa);
    ReflectionTestUtils.setField(g, "powEnforced", pow);
    ReflectionTestUtils.setField(g, "safeBrowsingEnabled", safeBrowsing);
    ReflectionTestUtils.setField(g, "googleClientSecret", google);
    return g;
  }

  @Test
  void nonProdSkipsEveryCheck() {
    ProdConfigGuard g = guard(false, "", "", "", false, false, "placeholder");
    assertThatCode(() -> g.run(null)).doesNotThrowAnyException();
  }

  @Test
  void prodWithBlankPrivateKeyAborts() {
    ProdConfigGuard g = guard(true, "", "pub", "key", true, true, "secret");
    assertThatThrownBy(() -> g.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_PRIVATE_KEY");
  }

  @Test
  void prodWithBlankPublicKeyAborts() {
    ProdConfigGuard g = guard(true, "priv", "  ", "key", true, true, "secret");
    assertThatThrownBy(() -> g.run(null)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void prodWithBlankTwofaKeyAborts() {
    ProdConfigGuard g = guard(true, "priv", "pub", "", true, true, "secret");
    assertThatThrownBy(() -> g.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TWOFA_AES_KEY");
  }

  @Test
  void prodWithSecretsSetButAllTogglesOffOnlyWarns() {
    ProdConfigGuard g = guard(true, "priv", "pub", "key", false, false, "placeholder");
    assertThatCode(() -> g.run(null)).doesNotThrowAnyException();
  }

  @Test
  void prodWithBlankGoogleSecretOnlyWarns() {
    ProdConfigGuard g = guard(true, "priv", "pub", "key", true, true, "");
    assertThatCode(() -> g.run(null)).doesNotThrowAnyException();
  }

  @Test
  void prodFullyConfiguredPasses() {
    ProdConfigGuard g = guard(true, "priv", "pub", "key", true, true, "real-secret");
    assertThatCode(() -> g.run(null)).doesNotThrowAnyException();
  }
}
