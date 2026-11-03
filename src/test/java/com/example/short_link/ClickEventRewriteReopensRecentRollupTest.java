package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

// From V213 on, click_recent_quarter and click_recent_dim hold sums of click_event days that still
// have raw rows. A migration that rewrites or deletes click_event rows must, in the same migration,
// delete those sums from the first day it touches and lower aggregated_until to that day (V213).
class ClickEventRewriteReopensRecentRollupTest {

  private static final int FIRST = 214;

  private static final List<Path> MIGRATIONS =
      List.of(Path.of("src/main/resources/db/migration"), Path.of("src/main/java/db/migration"));

  private static final Pattern VERSION = Pattern.compile("^V(\\d+)__");

  private static final Pattern REWRITE =
      Pattern.compile(
          "\\bUPDATE\\s+click_event\\b|\\bDELETE\\s+FROM\\s+click_event\\b"
              + "|\\bREPLACE\\s+INTO\\s+click_event\\b",
          Pattern.CASE_INSENSITIVE);

  @Test
  void aMigrationThatRewritesClicksReopensTheRecentSums() throws IOException {
    for (Path dir : MIGRATIONS) {
      try (Stream<Path> files = Files.list(dir)) {
        for (Path file : files.toList()) {
          Matcher version = VERSION.matcher(file.getFileName().toString());
          if (!version.find() || Integer.parseInt(version.group(1)) < FIRST) continue;
          String sql = Files.readString(file, StandardCharsets.UTF_8);
          if (!REWRITE.matcher(sql).find()) continue;
          assertThat(sql)
              .as(file + " rewrites click_event")
              .contains("aggregated_until", "click_recent_quarter", "click_recent_dim");
        }
      }
    }
  }
}
