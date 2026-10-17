package com.example.short_link.federation.application.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.RelayMode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
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
    assertThat(String.valueOf(application.getProperty("short-link.federation.relay.mode")))
        .isEqualTo("${FEDERATION_RELAY_MODE:}");
    assertThat(String.valueOf(application.getProperty("short-link.federation.relay.subscriptions")))
        .isEqualTo("${FEDERATION_RELAY_SUBSCRIPTIONS:}");
  }

  private static RelayProperties bound(String mode, String subscriptions) {
    return new Binder(
            new MapConfigurationPropertySource(
                Map.of(
                    "short-link.federation.relay.mode", mode,
                    "short-link.federation.relay.subscriptions", subscriptions)))
        .bindOrCreate("short-link.federation.relay", RelayProperties.class);
  }

  @Test
  void unsetModeAndSubscriptionsLeaveTheStoredSettingAndAskNoRelay() {
    RelayProperties unset = bound("", "");

    assertThat(unset.mode()).isNull();
    assertThat(unset.subscriptions()).isEmpty();
    assertThat(unset.enabled()).isFalse();
  }

  @Test
  void aModeAndACommaListOfRelaysAreReadAsWritten() {
    RelayProperties set =
        bound("store", "https://relay.fedi.buzz/tag/gaebal, https://relay.fedi.buzz/tag/enzinia,");

    assertThat(set.mode()).isEqualTo(RelayMode.STORE);
    assertThat(set.subscriptions())
        .containsExactly(
            "https://relay.fedi.buzz/tag/gaebal", "https://relay.fedi.buzz/tag/enzinia");
  }
}
