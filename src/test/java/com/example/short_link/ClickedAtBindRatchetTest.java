package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

// click_event.clicked_at holds the true instant, but Hibernate binds an Instant parameter of a
// native query as UTC wall-clock text, which MySQL reads in the session zone. So native SQL
// compares
// clicked_at only with FROM_UNIXTIME(:epoch). A baseline line is "<path under src/main/java><TAB>
// <comparison>", one line per occurrence.
class ClickedAtBindRatchetTest {

  static final int CEILING = 4;

  private static final Path MAIN = Path.of("src/main/java");
  private static final Path BASELINE = Path.of("src/test/resources/ratchet/clicked-at-binds.txt");

  private static final Pattern STRING_LITERAL =
      Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);

  private static final Pattern RAW_BIND =
      Pattern.compile(
          "\\bclicked_at\\s*(?:>=|<=|<>|=|<|>)\\s*:\\w+"
              + "|:\\w+\\s*(?:>=|<=|<>|=|<|>)\\s*(?:\\w+\\.)?clicked_at\\b"
              + "|\\bclicked_at\\s+BETWEEN\\s+:\\w+",
          Pattern.CASE_INSENSITIVE);

  @Test
  void nativeSqlComparesClickedAtOnlyWithAnEpochBind() throws IOException {
    List<String> found = rawBinds();
    List<String> added = new ArrayList<>(found);
    baseline().forEach(added::remove);

    assertThat(added)
        .as("Bind the instant as epoch seconds and compare clicked_at with FROM_UNIXTIME(:epoch)")
        .isEmpty();
    assertThat(baseline().size())
        .as("The baseline only shrinks; lower CEILING with it")
        .isLessThanOrEqualTo(CEILING);
  }

  @Test
  void theMatcherCatchesRawBindsAndLeavesEpochBindsAlone() {
    assertThat(
            List.of(
                "WHERE c.clicked_at >= :since",
                "AND clicked_at < :to",
                ":from <= c.clicked_at",
                "AND clicked_at BETWEEN :a AND :b"))
        .allMatch(sql -> RAW_BIND.matcher(sql).find());
    assertThat(
            List.of(
                "WHERE c.clicked_at >= FROM_UNIXTIME(:since)",
                "AND c.clickedAt >= :since",
                "clicked_at >= (SELECT rolled_until FROM click_rollup_state)"))
        .noneMatch(sql -> RAW_BIND.matcher(sql).find());
  }

  private static List<String> rawBinds() throws IOException {
    List<String> found = new ArrayList<>();
    try (Stream<Path> files = Files.walk(MAIN)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        StringBuilder sql = new StringBuilder();
        Matcher literal = STRING_LITERAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (literal.find()) {
          sql.append(literal.group(1) != null ? literal.group(1) : literal.group(2)).append(' ');
        }
        Matcher bind = RAW_BIND.matcher(sql.toString().replaceAll("\\s+", " "));
        while (bind.find()) {
          found.add(MAIN.relativize(file) + "\t" + bind.group());
        }
      }
    }
    return found;
  }

  private static List<String> baseline() throws IOException {
    return Files.readAllLines(BASELINE, StandardCharsets.UTF_8).stream()
        .filter(line -> !line.isBlank())
        .toList();
  }
}
