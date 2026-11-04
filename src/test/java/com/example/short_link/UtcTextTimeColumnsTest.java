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

// Hibernate binds an Instant through a UTC calendar, so created_at, expires_at and occurred_at hold
// UTC wall-clock text, which a TIMESTAMP column in the Asia/Seoul session reads as an instant nine
// hours early. click_event.clicked_at and the click rollup times are the true instant. SQL that
// puts
// both kinds side by side converts the click side to UTC text with TIMESTAMPADD(SECOND,
// UNIX_TIMESTAMP(x), '1970-01-01'), and never reads a UTC-text column through UNIX_TIMESTAMP,
// FROM_UNIXTIME or the session clock.
class UtcTextTimeColumnsTest {

  private static final Path MAIN = Path.of("src/main/java");

  private static final Pattern STRING_LITERAL =
      Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);

  private static final String UTC_TEXT =
      "(?:\\w+\\.)?(?:created_at|expires_at|occurred_at|createdAt|expiresAt|occurredAt)\\b";
  private static final String TRUE_INSTANT =
      "(?:\\w+\\.)?(?:clicked_at|hour_start|first_human_at|last_human_at|last_at|rolled_until"
          + "|clickedAt|hourStart|firstHumanAt|lastHumanAt|lastAt|rolledUntil)\\b";
  private static final String OP = "\\s*(?:>=|<=|<>|=|<|>)\\s*";
  private static final String SESSION_CLOCK =
      "\\b(?:FROM_UNIXTIME|NOW|CURRENT_TIMESTAMP|SYSDATE|CURDATE|CURRENT_DATE)\\b\\s*(?:\\([^()]*\\))?";

  static final Pattern MIXED =
      Pattern.compile(
          "TIMESTAMPDIFF\\(\\s*\\w+\\s*,\\s*"
              + UTC_TEXT
              + "\\s*,\\s*"
              + TRUE_INSTANT
              + "|TIMESTAMPDIFF\\(\\s*\\w+\\s*,\\s*"
              + TRUE_INSTANT
              + "\\s*,\\s*"
              + UTC_TEXT
              + "|"
              + TRUE_INSTANT
              + OP
              + UTC_TEXT
              + "|"
              + UTC_TEXT
              + OP
              + TRUE_INSTANT
              + "|UNIX_TIMESTAMP(?:\\(\\s*|'\\s*,\\s*)"
              + UTC_TEXT
              + "|"
              + UTC_TEXT
              + OP
              + SESSION_CLOCK
              + "|"
              + SESSION_CLOCK
              + OP
              + UTC_TEXT,
          Pattern.CASE_INSENSITIVE);

  @Test
  void sqlNeverMixesUtcTextColumnsWithTrueInstants() throws IOException {
    assertThat(mixed()).isEmpty();
  }

  @Test
  void theMatcherCatchesEachWayOfMixingAndLeavesConvertedSqlAlone() {
    assertThat(
            List.of(
                "TIMESTAMPDIFF(DAY, l.created_at, c.clicked_at)",
                "AND c.last_at >= u.created_at",
                "WHERE c.clickedAt >= l.createdAt",
                "FUNCTION('UNIX_TIMESTAMP', u.createdAt)",
                "UNIX_TIMESTAMP(created_at)",
                "WHERE created_at >= FROM_UNIXTIME(:since)",
                "AND expires_at < NOW()",
                "WHERE CURRENT_TIMESTAMP > l.expires_at"))
        .allMatch(sql -> MIXED.matcher(sql).find());
    assertThat(
            List.of(
                "TIMESTAMPDIFF(DAY, l.created_at, TIMESTAMPADD(SECOND, UNIX_TIMESTAMP(c.clicked_at),"
                    + " '1970-01-01'))",
                "AND c.seen_at >= u.created_at",
                "FUNCTION('CONVERT_TZ', u.createdAt, '+00:00', :tz)",
                "WHERE created_at >= :since",
                "AND expires_at < UTC_TIMESTAMP()",
                "c.clicked_at >= FROM_UNIXTIME(:from)",
                "c.clicked_at >= FROM_UNIXTIME(:from) AND :since <= created_at"))
        .noneMatch(sql -> MIXED.matcher(sql).find());
  }

  private static List<String> mixed() throws IOException {
    List<String> found = new ArrayList<>();
    try (Stream<Path> files = Files.walk(MAIN)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        StringBuilder sql = new StringBuilder();
        Matcher literal = STRING_LITERAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (literal.find()) {
          sql.append(literal.group(1) != null ? literal.group(1) : literal.group(2)).append(' ');
        }
        Matcher hit = MIXED.matcher(sql.toString().replaceAll("\\s+", " "));
        while (hit.find()) {
          found.add(MAIN.relativize(file) + "\t" + hit.group());
        }
      }
    }
    return found;
  }
}
