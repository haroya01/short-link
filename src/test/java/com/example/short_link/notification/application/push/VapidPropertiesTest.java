package com.example.short_link.notification.application.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VapidPropertiesTest {

  @Test
  void configuredOnlyWhenBothKeysPresent() {
    assertThat(new VapidProperties("pub", "priv", "mailto:x@y.com").configured()).isTrue();
    assertThat(new VapidProperties(null, "priv", null).configured()).isFalse();
    assertThat(new VapidProperties("pub", null, null).configured()).isFalse();
    assertThat(new VapidProperties("pub", "  ", null).configured()).isFalse();
  }

  @Test
  void defaultsSubjectWhenBlankKeepsItOtherwise() {
    assertThat(new VapidProperties("pub", "priv", null).subject())
        .isEqualTo("mailto:privacy@kurl.me");
    assertThat(new VapidProperties("pub", "priv", "  ").subject())
        .isEqualTo("mailto:privacy@kurl.me");
    assertThat(new VapidProperties("pub", "priv", "mailto:me@x.com").subject())
        .isEqualTo("mailto:me@x.com");
  }
}
