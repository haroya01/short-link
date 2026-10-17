package com.example.short_link.common.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ImageVariantsDeployEnvTest {

  private static final Pattern READ = Pattern.compile("\\$\\{(IMAGE_VARIANTS_[A-Z_]+):([^}]*)}");

  @Test
  void everyImageVariantSettingTheAppReadsIsPassedByComposeWithTheSameDefault() throws IOException {
    Map<String, String> read = new LinkedHashMap<>();
    Matcher matcher = READ.matcher(Files.readString(Path.of("src/main/resources/application.yml")));
    while (matcher.find()) {
      read.put(matcher.group(1), matcher.group(2));
    }
    String compose = Files.readString(Path.of("deploy/docker-compose.yml"));
    String example = Files.readString(Path.of("deploy/.env.example"));

    assertThat(read)
        .containsEntry("IMAGE_VARIANTS_ENABLED", "true")
        .containsEntry("IMAGE_VARIANTS_BACKFILL_ENABLED", "false")
        .containsEntry("IMAGE_VARIANTS_BACKFILL_INTERVAL", "PT1S");
    read.forEach(
        (name, fallback) -> {
          assertThat(compose)
              .as("docker-compose.yml passes %s", name)
              .contains(name + ": ${" + name + ":-" + fallback + "}");
          assertThat(example).as(".env.example names %s", name).contains(name + "=" + fallback);
        });
  }
}
