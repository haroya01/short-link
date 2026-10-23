package com.example.short_link.federation.domain.foryou;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class ForYouRankingGoldenTest {

  static final Path POOL = Path.of("src/test/resources/foryou-golden/pool.tsv");
  static final Path EXPECTED = Path.of("src/test/resources/foryou-golden/expected.txt");
  static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

  record Reader(String name, ForYouReader profile, Set<String> seen) {}

  static List<ForYouCandidate> pool() throws IOException {
    List<ForYouCandidate> pool = new ArrayList<>();
    for (String line : Files.readAllLines(POOL)) {
      if (line.isBlank() || line.startsWith("#")) {
        continue;
      }
      String[] cell = line.split("\t");
      long id = Long.parseLong(cell[0]);
      pool.add(
          new ForYouCandidate(
              id,
              uri(id),
              cell[1],
              cell[2],
              cell[3].equals("-") ? null : cell[3],
              cell[4].equals("-") ? Set.of() : Set.of(cell[4].split(",")),
              NOW.minus(Duration.ofMinutes(Long.parseLong(cell[5]))),
              Long.parseLong(cell[6]),
              Long.parseLong(cell[7]),
              Long.parseLong(cell[8]),
              id));
    }
    return pool;
  }

  static String uri(long id) {
    return "https://golden.example/statuses/" + id;
  }

  static List<Reader> readers() {
    Set<String> everyThird =
        LongStream.rangeClosed(1, 60)
            .filter(id -> id % 3 == 0)
            .mapToObj(ForYouRankingGoldenTest::uri)
            .collect(Collectors.toSet());
    return List.of(
        new Reader("no-language-no-signals", ForYouReader.NONE, Set.of()),
        new Reader("korean-browser", new ForYouReader(Set.of("ko"), Map.of(), Map.of()), Set.of()),
        new Reader(
            "japanese-java-reader",
            new ForYouReader(Set.of("ja"), Map.of("java", 3.0, "kotlin", 1.5), Map.of()),
            Set.of()),
        new Reader(
            "korean-loyal-to-a5",
            new ForYouReader(Set.of("ko"), Map.of("사진", 1.0), Map.of("a5", 3.0, "a7", 1.0)),
            Set.of()),
        new Reader(
            "korean-saw-every-third",
            new ForYouReader(Set.of("ko", "en"), Map.of("일상", 2.0), Map.of()),
            everyThird));
  }

  static String line(Reader reader, ForYouRanking.Ranked ranked) {
    return reader.name()
        + " | "
        + ranked.seenFrom()
        + " | "
        + ranked.items().stream().map(c -> c.ref().toString()).collect(Collectors.joining(" "));
  }

  static Map<String, String> expected() throws IOException {
    Map<String, String> lines = new LinkedHashMap<>();
    for (String line : Files.readAllLines(EXPECTED)) {
      if (!line.isBlank() && !line.startsWith("#")) {
        lines.put(line.substring(0, line.indexOf(" | ")), line);
      }
    }
    return lines;
  }

  @Test
  void theFixedPoolRanksExactlyAsRecordedForEveryReader() throws IOException {
    List<ForYouCandidate> pool = pool();
    Map<String, String> expected = expected();
    List<String> actual = new ArrayList<>();
    for (Reader reader : readers()) {
      actual.add(line(reader, ForYouRanking.rank(pool, reader.profile(), reader.seen(), NOW)));
    }

    assertThat(actual)
        .as(
            "a changed ranking of %s; replace %s with these lines only when the change is meant:%n%s",
            POOL, EXPECTED, String.join("\n", actual))
        .containsExactlyElementsOf(expected.values());
  }

  @Test
  void theFixturePoolKeepsTheSkewThatMakesTheCapsBite() throws IOException {
    List<ForYouCandidate> pool = pool();
    Map<String, Long> byAuthor =
        pool.stream()
            .collect(Collectors.groupingBy(ForYouCandidate::author, Collectors.counting()));
    Map<String, Long> byHost =
        pool.stream().collect(Collectors.groupingBy(ForYouCandidate::host, Collectors.counting()));

    assertThat(pool).hasSize(60);
    assertThat(byAuthor.values().stream().mapToLong(Long::longValue).max().orElse(0))
        .isGreaterThan(2 * 3);
    assertThat(byHost.values().stream().mapToLong(Long::longValue).max().orElse(0))
        .isGreaterThan(10 * 3);
    assertThat(Arrays.stream(new String[] {"ko", "ja", "en"}))
        .allMatch(lang -> pool.stream().anyMatch(c -> lang.equals(c.language())));
  }
}
