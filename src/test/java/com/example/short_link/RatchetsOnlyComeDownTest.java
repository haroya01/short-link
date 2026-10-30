package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

// Every ratchet total may only fall: frozen ArchUnit violations per rule, baseline constants and
// ceilings, PMD size findings summed per rule, and // comment lines. A total that rises between the
// base revision and this tree needs a line added to config/ratchet/raises.txt in the same change,
// "YYYY-MM-DD | <metric> | <old> -> <new> | <reason>", and the ledger only grows. A metric the base
// does not have is a newly introduced ratchet and needs no line.
class RatchetsOnlyComeDownTest {

  static final String LEDGER = "config/ratchet/raises.txt";

  private static final String STORE = "src/test/resources/archunit_store";
  private static final String TESTS = "src/test/java/com/example/short_link/";

  private static final Map<String, String> CONSTANTS =
      Map.of(
          "ArchUnitSemanticRulesTest.WALL_CLOCK_BASELINE", "ArchUnitSemanticRulesTest.java",
          "ArchUnitSemanticRulesTest.ENTITY_SETTER_BASELINE", "ArchUnitSemanticRulesTest.java",
          "ArchitectureRulesTest.SPRING_BOOT_CONTROLLER_TEST_BASELINE",
              "ArchitectureRulesTest.java",
          "CrossModuleSqlRatchetTest.CEILING", "CrossModuleSqlRatchetTest.java",
          "ClickedAtBindRatchetTest.CEILING", "ClickedAtBindRatchetTest.java",
          "HistoryCommentRatchetTest.CEILING", "HistoryCommentRatchetTest.java");

  private static final Pattern LEDGER_LINE =
      Pattern.compile(
          "^\\s*\\d{4}-\\d{2}-\\d{2}\\s*\\|\\s*(\\S+)\\s*\\|\\s*(\\d+)\\s*->\\s*(\\d+)"
              + "\\s*\\|\\s*(.*\\S)\\s*$");

  private static final int SHORTEST_REASON = 10;

  @Test
  void noRatchetRisesWithoutARecordedReason() {
    Optional<String> base = baseRevision();
    if (base.isEmpty()) {
      assertThat(System.getenv("CI"))
          .as("CI must give this test a base revision (RATCHET_BASE, fetch-depth 2)")
          .isNull();
      Assumptions.abort("No base revision to compare with; set RATCHET_BASE or fetch origin/main");
    }
    Function<String, Optional<String>> before = path -> show(base.get(), path);
    Function<String, Optional<String>> after =
        Optional.ofNullable(System.getenv("RATCHET_HEAD"))
            .<Function<String, Optional<String>>>map(head -> path -> show(head, path))
            .orElse(RatchetsOnlyComeDownTest::readWorkingTree);

    List<String> baseLedger = ledgerLines(before.apply(LEDGER));
    List<String> headLedger = ledgerLines(after.apply(LEDGER));
    List<String> problems = new ArrayList<>(ledgerEdits(baseLedger, headLedger));
    problems.addAll(
        unexplainedRaises(totals(before), totals(after), added(baseLedger, headLedger)));

    assertThat(problems).as("Ratchets compared with %s", base.get()).isEmpty();
  }

  @Test
  void aRaiseNeedsALedgerLineWithItsExactNumbersAndAReason() {
    Map<String, Long> base = Map.of("pmd:CyclomaticComplexity", 146L, "comments", 1519L);
    Map<String, Long> raised = Map.of("pmd:CyclomaticComplexity", 150L, "comments", 1519L);
    String reason = "새 계산 경로는 분기를 나눌 수 없다(정산 규칙 표 그대로)";

    assertThat(unexplainedRaises(base, raised, List.of())).hasSize(1);
    assertThat(
            unexplainedRaises(
                base,
                raised,
                List.of("2026-10-30 | pmd:CyclomaticComplexity | 146 -> 150 | " + reason)))
        .isEmpty();
    assertThat(
            unexplainedRaises(
                base,
                raised,
                List.of("2026-10-30 | pmd:CyclomaticComplexity | 140 -> 150 | " + reason)))
        .hasSize(1);
    assertThat(
            unexplainedRaises(
                base, raised, List.of("2026-10-30 | pmd:CyclomaticComplexity | 146 -> 150 | 필요")))
        .hasSize(1);
  }

  @Test
  void aFallANewRatchetAndAnUnchangedTotalNeedNoLine() {
    Map<String, Long> base = Map.of("comments", 1519L, "archunit:moduleBoundaries", 6L);
    Map<String, Long> head =
        Map.of(
            "comments", 1500L, "archunit:moduleBoundaries", 6L, "pmd:AvoidDeeplyNestedIfStmts", 9L);

    assertThat(unexplainedRaises(base, head, List.of())).isEmpty();
  }

  @Test
  void theLedgerOnlyGrows() {
    String kept = "2026-06-11 | x | 1 -> 2 | 이미 남긴 이유 한 줄입니다";

    assertThat(ledgerEdits(List.of(kept), List.of(kept, "2026-10-30 | y | 3 -> 4 | 새 이유를 적습니다")))
        .isEmpty();
    assertThat(ledgerEdits(List.of(kept), List.of())).hasSize(1);
  }

  @Test
  void totalsReadEveryKindOfRatchet() {
    Map<String, String> files =
        Map.of(
            STORE + "/stored.rules",
            "#\nmoduleBoundaries=aaa\n",
            STORE + "/aaa",
            "one\ntwo\n\n",
            TESTS + "ArchitectureRulesTest.java",
            "private static final int SPRING_BOOT_CONTROLLER_TEST_BASELINE = 37;",
            "config/pmd/size-baseline.txt",
            "# h\nNPathComplexity|a.java|m()#1=2831155200\nNPathComplexity|b.java|n()#1=5\n",
            "config/comments/comment-baseline.txt",
            "src/main/java/a/A.java=3\nsrc/main/java/b/B.java=4\n");

    assertThat(totals(path -> Optional.ofNullable(files.get(path))))
        .containsEntry("archunit:moduleBoundaries", 2L)
        .containsEntry("ArchitectureRulesTest.SPRING_BOOT_CONTROLLER_TEST_BASELINE", 37L)
        .containsEntry("pmd:NPathComplexity", 2831155205L)
        .containsEntry("comments", 7L)
        .doesNotContainKey("ClickedAtBindRatchetTest.CEILING");
  }

  static Map<String, Long> totals(Function<String, Optional<String>> read) {
    Map<String, Long> totals = new LinkedHashMap<>();
    read.apply(STORE + "/stored.rules")
        .ifPresent(
            rules ->
                rules
                    .lines()
                    .filter(line -> line.contains("=") && !line.startsWith("#"))
                    .forEach(
                        line -> {
                          String[] ruleAndFile = line.split("=", 2);
                          read.apply(STORE + "/" + ruleAndFile[1].strip())
                              .ifPresent(
                                  store ->
                                      totals.put(
                                          "archunit:" + ruleAndFile[0].strip(),
                                          store.lines().filter(l -> !l.isBlank()).count()));
                        }));
    CONSTANTS.forEach(
        (key, file) ->
            read.apply(TESTS + file)
                .flatMap(source -> constant(source, key.substring(key.indexOf('.') + 1)))
                .ifPresent(value -> totals.put(key, value)));
    read.apply("config/pmd/size-baseline.txt")
        .ifPresent(
            baseline ->
                baseline
                    .lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .forEach(
                        line ->
                            totals.merge(
                                "pmd:" + line.substring(0, line.indexOf('|')),
                                Long.parseLong(line.substring(line.lastIndexOf('=') + 1).strip()),
                                Long::sum)));
    read.apply("config/comments/comment-baseline.txt")
        .ifPresent(
            baseline ->
                totals.put(
                    "comments",
                    baseline
                        .lines()
                        .filter(line -> line.contains("="))
                        .mapToLong(
                            line ->
                                Long.parseLong(line.substring(line.lastIndexOf('=') + 1).strip()))
                        .sum()));
    return totals;
  }

  static List<String> unexplainedRaises(
      Map<String, Long> base, Map<String, Long> head, List<String> addedLedgerLines) {
    List<String> problems = new ArrayList<>();
    head.forEach(
        (metric, now) -> {
          Long before = base.get(metric);
          if (before == null || now <= before) {
            return;
          }
          boolean recorded =
              addedLedgerLines.stream()
                  .map(LEDGER_LINE::matcher)
                  .filter(Matcher::matches)
                  .anyMatch(
                      m ->
                          m.group(1).equals(metric)
                              && Long.parseLong(m.group(2)) == before
                              && Long.parseLong(m.group(3)) == now
                              && m.group(4).length() >= SHORTEST_REASON);
          if (!recorded) {
            problems.add(
                String.format(
                    "%s rose %d -> %d. If it must, add to %s: YYYY-MM-DD | %s | %d -> %d | <why>",
                    metric, before, now, LEDGER, metric, before, now));
          }
        });
    return problems;
  }

  static List<String> ledgerEdits(List<String> base, List<String> head) {
    List<String> missing = new ArrayList<>(base);
    head.forEach(missing::remove);
    return missing.stream()
        .map(line -> "The ledger only grows; this line was removed or edited: " + line)
        .toList();
  }

  private static List<String> added(List<String> base, List<String> head) {
    List<String> added = new ArrayList<>(head);
    base.forEach(added::remove);
    return added;
  }

  private static List<String> ledgerLines(Optional<String> ledger) {
    return ledger
        .map(text -> text.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).toList())
        .orElse(List.of());
  }

  private static Optional<Long> constant(String source, String name) {
    Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*(\\d+)\\s*;").matcher(source);
    return m.find() ? Optional.of(Long.parseLong(m.group(1))) : Optional.empty();
  }

  private static Optional<String> baseRevision() {
    String asked = System.getenv("RATCHET_BASE");
    if (asked != null) {
      return git("rev-parse", "--verify", asked + "^{commit}").map(String::strip);
    }
    Optional<String> head = git("rev-parse", "HEAD").map(String::strip);
    return git("merge-base", "HEAD", "origin/main")
        .map(String::strip)
        .filter(b -> !b.equals(head.orElse(null)));
  }

  private static Optional<String> show(String revision, String path) {
    return git("show", revision + ":" + path);
  }

  private static Optional<String> readWorkingTree(String path) {
    Path file = Path.of(path);
    try {
      return Files.exists(file)
          ? Optional.of(Files.readString(file, StandardCharsets.UTF_8))
          : Optional.empty();
    } catch (IOException e) {
      throw new IllegalStateException("cannot read " + path, e);
    }
  }

  private static Optional<String> git(String... args) {
    List<String> command = new ArrayList<>(List.of("git"));
    command.addAll(List.of(args));
    try {
      Process process =
          new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      return process.waitFor() == 0 ? Optional.of(out) : Optional.empty();
    } catch (IOException e) {
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }
}
