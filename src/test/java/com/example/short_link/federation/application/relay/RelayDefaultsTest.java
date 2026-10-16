package com.example.short_link.federation.application.relay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class RelayDefaultsTest {

  @Test
  void relaysAreOffUnlessTheServerIsToldOtherwise() throws Exception {
    PropertySource<?> application =
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))
            .getFirst();

    assertThat(String.valueOf(application.getProperty("short-link.federation.relay.enabled")))
        .isEqualTo("${FEDERATION_RELAY_ENABLED:false}");
    assertThat(String.valueOf(application.getProperty("short-link.federation.relay.per-minute")))
        .isEqualTo("${FEDERATION_RELAY_PER_MINUTE:30}");
    assertThat(String.valueOf(application.getProperty("short-link.federation.relay.per-day")))
        .isEqualTo("${FEDERATION_RELAY_PER_DAY:2000}");
  }
}
