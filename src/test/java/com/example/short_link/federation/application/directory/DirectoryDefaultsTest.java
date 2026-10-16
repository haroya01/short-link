package com.example.short_link.federation.application.directory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class DirectoryDefaultsTest {

  @Test
  void directoriesAreOffUnlessTheServerIsToldOtherwise() throws Exception {
    PropertySource<?> application =
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))
            .getFirst();

    assertThat(String.valueOf(application.getProperty("short-link.federation.directory.enabled")))
        .isEqualTo("${FEDERATION_DIRECTORY_ENABLED:false}");
  }
}
