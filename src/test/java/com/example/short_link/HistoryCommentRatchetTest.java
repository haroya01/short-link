package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

// A baseline line is "<path under src/main/java><TAB><comment text>", so moving a comment to
// another line does not break it.
class HistoryCommentRatchetTest {

  static final int CEILING = 0;

  private static final Path MAIN = Path.of("src/main/java");
  private static final Path BASELINE = Path.of("src/test/resources/ratchet/history-comments.txt");

  private static final Pattern HISTORY =
      Pattern.compile(
          "(?<![\\w/&])#\\d+\\b"
              + "|\\bPR\\s*#?\\d+"
              + "|\\b(previously|formerly)\\b"
              + "|\\bused to\\b"
              + "|\\bwas (replaced|removed|renamed)\\b"
              + "|\\bno longer used\\b"
              + "|\\bwe (removed|replaced|changed|switched)\\b"
              + "|\\buntil (now|recently)\\b"
              + "|예전에?는|예전엔|이전에는|이전엔|원래는"
              + "|폐기했|바뀌었다|바꿨다|바꾸었다|없앴다|되돌렸다",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");

  @Test
  void noNewCommentTellsTheCodesHistory() throws IOException {
    Set<String> found = historyComments();
    Set<String> baseline = baseline();

    Set<String> added = new TreeSet<>(found);
    added.removeAll(baseline);
    assertThat(added)
        .as(
            "History belongs in the commit message and the PR body. Say what holds now, or drop"
                + " the comment.")
        .isEmpty();

    Set<String> gone = new TreeSet<>(baseline);
    gone.removeAll(found);
    assertThat(gone).as("Fixed, so remove these lines from %s", BASELINE).isEmpty();

    assertThat(baseline.size())
        .as("The baseline only shrinks; lower CEILING with it")
        .isLessThanOrEqualTo(CEILING);
  }

  @Test
  void theMatcherCatchesHistoryAndLeavesContractsAlone() {
    assertThat(
            List.of(
                "Moved here in #812 so the cache sees it",
                "see PR 640",
                "Previously this read the whole table",
                "This used to block the request thread",
                "The old flag was replaced by a column",
                "예전에는 요청마다 다시 계산했다",
                "원래는 동기로 보냈다",
                "구독 행을 폐기했다"))
        .allMatch(HistoryCommentRatchetTest::tellsHistory);
    assertThat(
            List.of(
                "Unprefixed legacy rows are returned as plaintext",
                "옛 앱도 이 모양으로 보낸다",
                "구형 클라이언트의 reason을 변환한다",
                "404/410 응답은 구독을 폐기한다",
                "A note that can no longer be posted stays with the reason",
                "Fragment ids look like https://kurl.me/a#12",
                "HTML entity &#39; stays escaped"))
        .noneMatch(HistoryCommentRatchetTest::tellsHistory);
  }

  static boolean tellsHistory(String comment) {
    return HISTORY.matcher(comment).find();
  }

  private static Set<String> historyComments() throws IOException {
    Set<String> found = new TreeSet<>();
    try (Stream<Path> sources = Files.walk(MAIN)) {
      for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
        String path = MAIN.relativize(source).toString().replace('\\', '/');
        boolean textBlock = false;
        for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
          if (line.split("\"\"\"", -1).length % 2 == 0) {
            textBlock = !textBlock;
            continue;
          }
          if (textBlock) {
            continue;
          }
          String code =
              STRING_LITERAL
                  .matcher(line)
                  .replaceAll(literal -> "_".repeat(literal.group().length()));
          int comment = code.indexOf("//");
          if (comment < 0) {
            continue;
          }
          String text = line.substring(comment + 2).strip();
          if (tellsHistory(text)) {
            found.add(path + "\t" + text);
          }
        }
      }
    }
    return found;
  }

  private static Set<String> baseline() throws IOException {
    Set<String> lines = new TreeSet<>();
    for (String line : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
      if (!line.isBlank()) {
        lines.add(line);
      }
    }
    return lines;
  }
}
