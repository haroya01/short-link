package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class TrendsDefaultsTest {

  private static final String HOSTS =
      "mastodon.social,mstdn.jp,fedibird.com,qdon.space,planet.moe,social.silicon.moe";

  @Test
  void trendsAreOffUntilTheServerIsToldAndProductionPassesBothSettings() throws Exception {
    PropertySource<?> application =
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))
            .getFirst();

    assertThat(String.valueOf(application.getProperty("short-link.federation.trends.enabled")))
        .isEqualTo("${FEDERATION_TRENDS_ENABLED:false}");
    assertThat(String.valueOf(application.getProperty("short-link.federation.trends.hosts")))
        .isEqualTo("${FEDERATION_TRENDS_HOSTS:" + HOSTS + "}");
    String compose = Files.readString(Path.of("deploy/docker-compose.yml"));
    assertThat(compose)
        .contains("FEDERATION_TRENDS_ENABLED: ${FEDERATION_TRENDS_ENABLED:-false}")
        .contains("FEDERATION_TRENDS_HOSTS: ${FEDERATION_TRENDS_HOSTS:-" + HOSTS + "}");
  }
}
