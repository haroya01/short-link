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

// DATETIME columns hold UTC wall-clock time, the way Hibernate writes an Instant. The session runs
// in KST, so the SQL clock functions that answer in the session zone give a time nine hours ahead.
// Native SQL takes the time as a bound Instant or UTC_TIMESTAMP().
class SqlClockRatchetTest {

  private static final Path MAIN = Path.of("src/main/java");
  private static final Path APPLIED_MIGRATIONS = MAIN.resolve("db/migration");

  private static final Pattern STRING_LITERAL =
      Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);

  private static final Pattern SESSION_CLOCK =
      Pattern.compile(
          "(?<!\\.)\\b(?:NOW|SYSDATE|CURDATE|CURTIME|LOCALTIME|LOCALTIMESTAMP)\\s*\\("
              + "|\\bCURRENT_(?:TIMESTAMP|DATE|TIME)\\b",
          Pattern.CASE_INSENSITIVE);

  @Test
  void nativeSqlNeverReadsTheSessionClock() throws IOException {
    assertThat(sessionClocks())
        .as("Bind an Instant or use UTC_TIMESTAMP(6); the session clock is KST wall-clock time")
        .isEmpty();
  }

  @Test
  void theMatcherCatchesSessionClocksAndLeavesUtcAlone() {
    assertThat(
            List.of(
                "VALUES (:postId, :userId, NOW())",
                "VALUES (:userId, CURRENT_TIMESTAMP(6))",
                "WHERE d = CURDATE()",
                "SET at = sysdate()",
                "UPDATE room_invite SET disabled_at = NOW(6)"))
        .allMatch(sql -> SESSION_CLOCK.matcher(sql).find());
    assertThat(
            List.of(
                "VALUES (:postId, :userId, UTC_TIMESTAMP(6))",
                "WHERE published_at <= :now",
                "the current time is known",
                "var ms = at - Date.now();"))
        .noneMatch(sql -> SESSION_CLOCK.matcher(sql).find());
  }

  private static List<String> sessionClocks() throws IOException {
    List<String> found = new ArrayList<>();
    try (Stream<Path> files = Files.walk(MAIN)) {
      for (Path file :
          files
              .filter(p -> p.toString().endsWith(".java"))
              .filter(p -> !p.startsWith(APPLIED_MIGRATIONS))
              .sorted()
              .toList()) {
        Matcher literal = STRING_LITERAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (literal.find()) {
          String text = literal.group(1) != null ? literal.group(1) : literal.group(2);
          Matcher clock = SESSION_CLOCK.matcher(text);
          while (clock.find()) {
            found.add(MAIN.relativize(file) + "\t" + clock.group());
          }
        }
      }
    }
    return found;
  }
}
