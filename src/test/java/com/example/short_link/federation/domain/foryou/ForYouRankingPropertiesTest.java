package com.example.short_link.federation.domain.foryou;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.foryou.ForYouRanking.Ranked;
import com.example.short_link.federation.domain.foryou.ForYouRanking.Weights;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ForYouRankingPropertiesTest {

  private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
  private static final int TRIALS = 300;
  private static final List<String> LANGUAGES = List.of("ko", "ja", "en", "de");
  private static final List<String> TAGS = List.of("java", "일상", "사진", "음악", "게임", "요리", "kotlin");

  private record Trial(long seed, Random random) {

    String at() {
      return "seed " + seed;
    }
  }

  private static Trial trial(int index) {
    long seed = 20_261_023L * 31 + index;
    return new Trial(seed, new Random(seed));
  }

  private static ForYouCandidate candidate(
      long order,
      String author,
      String host,
      String language,
      Set<String> tags,
      Duration age,
      long likes,
      long reposts,
      long replies) {
    return new ForYouCandidate(
        order,
        "https://" + host + "/users/" + author + "/statuses/" + order,
        author,
        host,
        language,
        tags,
        NOW.minus(age),
        likes,
        reposts,
        replies,
        order);
  }

  private static List<ForYouCandidate> pool(Random random, int size, long firstOrder) {
    int authors = 1 + random.nextInt(40);
    int hosts = 1 + random.nextInt(8);
    List<ForYouCandidate> pool = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      int author = (int) Math.floor(authors * Math.pow(random.nextDouble(), 2));
      String language =
          random.nextInt(8) == 0 ? null : LANGUAGES.get(random.nextInt(LANGUAGES.size()));
      pool.add(
          candidate(
              firstOrder + i,
              "a" + author,
              "h" + (author % hosts) + ".example",
              language,
              tags(random),
              Duration.ofMinutes(random.nextInt(72 * 60)),
              random.nextInt(4) == 0 ? random.nextInt(2_000) : random.nextInt(8),
              random.nextInt(5) == 0 ? random.nextInt(50) : 0,
              random.nextInt(5) == 0 ? random.nextInt(20) : 0));
    }
    return pool;
  }

  private static Set<String> tags(Random random) {
    Set<String> tags = new HashSet<>();
    if (random.nextInt(10) < 3) {
      int count = 1 + random.nextInt(3);
      for (int i = 0; i < count; i++) {
        tags.add(TAGS.get(random.nextInt(TAGS.size())));
      }
    }
    return tags;
  }

  private static ForYouReader reader(Random random) {
    Set<String> languages = new HashSet<>();
    for (String language : LANGUAGES) {
      if (random.nextInt(3) == 0) {
        languages.add(language);
      }
    }
    Map<String, Double> tags = new HashMap<>();
    for (String tag : TAGS) {
      if (random.nextBoolean()) {
        tags.put(tag, 0.5 + random.nextInt(10) * 0.5);
      }
    }
    Map<String, Double> authors = new HashMap<>();
    for (int i = 0; i < 40; i++) {
      if (random.nextInt(6) == 0) {
        authors.put("a" + i, 0.5 + random.nextInt(12) * 0.5);
      }
    }
    return new ForYouReader(languages, tags, authors);
  }

  private static Set<String> seen(Random random, List<ForYouCandidate> pool) {
    double share = random.nextInt(3) == 0 ? 0 : random.nextDouble();
    return pool.stream()
        .filter(candidate -> random.nextDouble() < share)
        .map(ForYouCandidate::uri)
        .collect(Collectors.toSet());
  }

  private static List<String> uris(Ranked ranked) {
    return ranked.items().stream().map(ForYouCandidate::uri).toList();
  }

  @Test
  void theRankingNeverDependsOnTheOrderThePoolCameIn() {
    for (int t = 0; t < TRIALS; t++) {
      Trial trial = trial(t);
      List<ForYouCandidate> pool = pool(trial.random(), trial.random().nextInt(150), 1);
      ForYouReader reader = reader(trial.random());
      Set<String> seen = seen(trial.random(), pool);
      Ranked first = ForYouRanking.rank(pool, reader, seen, NOW);

      for (int shuffle = 0; shuffle < 4; shuffle++) {
        List<ForYouCandidate> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, trial.random());
        Ranked again = ForYouRanking.rank(shuffled, reader, seen, NOW);

        assertThat(uris(again)).as(trial.at()).isEqualTo(uris(first));
        assertThat(again.seenFrom()).as(trial.at()).isEqualTo(first.seenFrom());
      }
    }
  }

  @Test
  void everyNoteComesOnceUnseenBeforeSeenAndSeenFromMarksTheFirstSeen() {
    for (int t = 0; t < TRIALS; t++) {
      Trial trial = trial(t);
      List<ForYouCandidate> pool = pool(trial.random(), trial.random().nextInt(500), 1);
      Set<String> seen = seen(trial.random(), pool);
      Ranked ranked = ForYouRanking.rank(pool, reader(trial.random()), seen, NOW);
      List<String> uris = uris(ranked);
      long unseen = pool.stream().filter(candidate -> !seen.contains(candidate.uri())).count();

      assertThat(new HashSet<>(uris)).as(trial.at()).hasSameSizeAs(uris);
      assertThat(uris)
          .as(trial.at())
          .hasSize(Math.min(pool.size(), ForYouRanking.MAX_RANKED))
          .isSubsetOf(pool.stream().map(ForYouCandidate::uri).toList());
      if (seen.isEmpty() || unseen >= ForYouRanking.MAX_RANKED) {
        assertThat(ranked.seenFrom()).as(trial.at()).isEqualTo(-1);
        assertThat(uris).as(trial.at()).noneMatch(seen::contains);
      } else {
        assertThat(ranked.seenFrom()).as(trial.at()).isEqualTo((int) unseen);
        assertThat(uris.subList(0, ranked.seenFrom())).as(trial.at()).noneMatch(seen::contains);
        assertThat(uris.subList(ranked.seenFrom(), uris.size()))
            .as(trial.at())
            .allMatch(seen::contains);
      }
    }
  }

  private record Pair(ForYouCandidate better, ForYouCandidate worse) {}

  private static int placeOf(Ranked ranked, ForYouCandidate candidate) {
    return uris(ranked).indexOf(candidate.uri());
  }

  private static void betterComesFirst(Trial trial, ForYouReader reader, Pair pair) {
    List<ForYouCandidate> pool = pool(trial.random(), trial.random().nextInt(200), 10);
    pool.add(pair.worse());
    pool.add(pair.better());
    Collections.shuffle(pool, trial.random());
    Ranked ranked = ForYouRanking.rank(pool, reader, Set.of(), NOW);

    assertThat(placeOf(ranked, pair.better()))
        .as(trial.at())
        .isNotNegative()
        .isLessThan(placeOf(ranked, pair.worse()));
  }

  private static ForYouCandidate twin(
      long order, String language, Set<String> tags, Duration age, long likes, long reposts) {
    return candidate(
        order, "twin" + order, "twin" + order + ".example", language, tags, age, likes, reposts, 0);
  }

  @Test
  void withAllElseEqualMoreReactionsNeverRankLower() {
    for (int t = 0; t < TRIALS; t++) {
      Trial trial = trial(t);
      Random random = trial.random();
      String language = LANGUAGES.get(random.nextInt(LANGUAGES.size()));
      Set<String> tags = tags(random);
      Duration age = Duration.ofMinutes(random.nextInt(72 * 60));
      long likes = random.nextInt(500);
      long more = likes + 1 + random.nextInt(500);
      boolean byBoosts = random.nextBoolean();

      betterComesFirst(
          trial,
          reader(random),
          new Pair(
              twin(1, language, tags, age, byBoosts ? likes : more, byBoosts ? 1 : 0),
              twin(2, language, tags, age, likes, 0)));
    }
  }

  @Test
  void withAllElseEqualThePostInTheReadersLanguageComesFirstAndAnUnknownLanguageSitsBetween() {
    for (int t = 0; t < TRIALS; t++) {
      Trial trial = trial(t);
      Random random = trial.random();
      ForYouReader drawn = reader(random);
      String reads = LANGUAGES.get(random.nextInt(LANGUAGES.size()));
      Set<String> languages = new HashSet<>(drawn.languages());
      languages.add(reads);
      String foreign =
          LANGUAGES.stream().filter(lang -> !languages.contains(lang)).findFirst().orElse("fr");
      ForYouReader reader = new ForYouReader(languages, drawn.tags(), drawn.authors());
      Set<String> tags = tags(random);
      Duration age = Duration.ofMinutes(random.nextInt(72 * 60));
      long likes = random.nextInt(500);

      betterComesFirst(
          trial,
          reader,
          new Pair(twin(1, reads, tags, age, likes, 0), twin(2, foreign, tags, age, likes, 0)));
      betterComesFirst(
          trial,
          reader,
          new Pair(twin(3, reads, tags, age, likes, 0), twin(4, null, tags, age, likes, 0)));
      betterComesFirst(
          trial,
          reader,
          new Pair(twin(5, null, tags, age, likes, 0), twin(6, foreign, tags, age, likes, 0)));
    }
  }

  @Test
  void withAllElseEqualTheNewerPostComesFirst() {
    for (int t = 0; t < TRIALS; t++) {
      Trial trial = trial(t);
      Random random = trial.random();
      String language = LANGUAGES.get(random.nextInt(LANGUAGES.size()));
      Set<String> tags = tags(random);
      long likes = random.nextInt(500);
      Duration newer = Duration.ofMinutes(random.nextInt(72 * 60));
      Duration older = newer.plusMinutes(1 + random.nextInt(24 * 60));

      betterComesFirst(
          trial,
          reader(random),
          new Pair(
              twin(1, language, tags, newer, likes, 0), twin(2, language, tags, older, likes, 0)));
    }
  }

  @Test
  void withAllElseEqualATagTheReaderCaresForOrAnAuthorTheyReadNeverRanksLower() {
    for (int t = 0; t < TRIALS; t++) {
      Trial trial = trial(t);
      Random random = trial.random();
      String language = LANGUAGES.get(random.nextInt(LANGUAGES.size()));
      Duration age = Duration.ofMinutes(random.nextInt(72 * 60));
      long likes = random.nextInt(500);
      ForYouReader drawn = reader(random);
      Map<String, Double> tags = new HashMap<>(drawn.tags());
      tags.put("java", 1.0 + random.nextInt(5));
      tags.remove("kotlin");
      Map<String, Double> authors = new HashMap<>(drawn.authors());
      authors.put("twin3", 0.5 + random.nextInt(6) * 0.5);
      ForYouReader reader = new ForYouReader(drawn.languages(), tags, authors);

      betterComesFirst(
          trial,
          reader,
          new Pair(
              twin(1, language, Set.of("java"), age, likes, 0),
              twin(2, language, Set.of("kotlin"), age, likes, 0)));
      betterComesFirst(
          trial,
          reader,
          new Pair(
              twin(3, language, Set.of(), age, likes, 0),
              twin(4, language, Set.of(), age, likes, 0)));
    }
  }

  @Test
  void theInterestTermStaysWithinZeroAndOneForAnyReaderAndPool() {
    Duration scale = Duration.ofHours(48);
    Weights interestOnly = new Weights(0, 0, 1, scale, 20, 1_000, 1_000);
    for (int t = 0; t < TRIALS; t++) {
      Trial trial = trial(t);
      Random random = trial.random();
      List<ForYouCandidate> pool = new ArrayList<>();
      for (ForYouCandidate drawn : pool(random, 1 + random.nextInt(120), 10)) {
        pool.add(
            new ForYouCandidate(
                drawn.ref(),
                drawn.uri(),
                drawn.author(),
                drawn.host(),
                drawn.language(),
                drawn.tags(),
                NOW.minus(scale.multipliedBy(3)),
                drawn.likes(),
                drawn.reposts(),
                drawn.replies(),
                drawn.order()));
      }
      Map<String, Double> authors = new HashMap<>(reader(random).authors());
      pool.forEach(c -> authors.merge(c.author(), random.nextInt(8) * 0.5, Double::sum));
      ForYouReader reader = new ForYouReader(Set.of(), reader(random).tags(), authors);
      ForYouCandidate ceiling =
          candidate(
              1,
              "probe-top",
              "probe.example",
              null,
              Set.of(),
              scale.multipliedBy(2).minusSeconds(1),
              0,
              0,
              0);
      ForYouCandidate floor =
          candidate(
              2,
              "probe-bottom",
              "probe.example",
              null,
              Set.of(),
              scale.multipliedBy(3).plusSeconds(1),
              0,
              0,
              0);
      pool.add(ceiling);
      pool.add(floor);

      List<String> ranked = uris(ForYouRanking.rank(pool, reader, Set.of(), NOW, interestOnly));

      assertThat(ranked.getFirst()).as(trial.at()).isEqualTo(ceiling.uri());
      assertThat(ranked.getLast()).as(trial.at()).isEqualTo(floor.uri());
    }
  }

  @Test
  void theLanguageTermIsOneHalfOrZeroAndZeroForAReaderWithNoLanguage() {
    for (int t = 0; t < TRIALS; t++) {
      Trial trial = trial(t);
      ForYouReader reader = reader(trial.random());
      for (ForYouCandidate candidate : pool(trial.random(), 20, 1)) {
        double term = ForYouRanking.language(candidate, reader);
        if (reader.languages().isEmpty()) {
          assertThat(term).as(trial.at()).isZero();
        } else if (candidate.language() == null) {
          assertThat(term).as(trial.at()).isEqualTo(0.5);
        } else {
          assertThat(term)
              .as(trial.at())
              .isEqualTo(reader.languages().contains(candidate.language()) ? 1.0 : 0.0);
        }
      }
    }
  }

  private static List<ForYouCandidate> crowded(Random random, int fillers) {
    List<ForYouCandidate> pool = new ArrayList<>();
    long order = 1;
    int heavy = 1 + random.nextInt(4);
    for (int a = 0; a < heavy; a++) {
      int posts = 10 + random.nextInt(60);
      String host = "big" + random.nextInt(2) + ".example";
      for (int i = 0; i < posts; i++) {
        pool.add(
            candidate(
                order++,
                "loud" + a,
                host,
                "ko",
                tags(random),
                Duration.ofMinutes(random.nextInt(72 * 60)),
                200 + random.nextInt(2_000),
                0,
                0));
      }
    }
    for (int i = 0; i < fillers; i++) {
      pool.add(
          candidate(
              order++,
              "filler" + i,
              "small" + i / 5 + ".example",
              random.nextBoolean() ? "ko" : "ja",
              tags(random),
              Duration.ofMinutes(random.nextInt(72 * 60)),
              random.nextInt(40),
              0,
              0));
    }
    Collections.shuffle(pool, random);
    return pool;
  }

  static void eachBlockHoldsItsCaps(String at, List<ForYouCandidate> list, int blocks) {
    for (int block = 0; block < blocks && block * 20 < list.size(); block++) {
      List<ForYouCandidate> twenty =
          list.subList(block * 20, Math.min(list.size(), block * 20 + 20));
      Map<String, Long> byAuthor =
          twenty.stream()
              .collect(Collectors.groupingBy(ForYouCandidate::author, Collectors.counting()));
      Map<String, Long> byHost =
          twenty.stream()
              .collect(Collectors.groupingBy(ForYouCandidate::host, Collectors.counting()));
      assertThat(byAuthor.values()).as(at + " block " + block).allMatch(count -> count <= 2);
      assertThat(byHost.values()).as(at + " block " + block).allMatch(count -> count <= 10);
    }
  }

  @Test
  void everyBlockOfTwentyHoldsTwoPerAuthorAndTenPerServerWhileOthersAreLeftToFillIt() {
    for (int t = 0; t < TRIALS; t++) {
      Trial trial = trial(t);
      int fillers = 20 + trial.random().nextInt(300);
      List<ForYouCandidate> pool = crowded(trial.random(), fillers);
      Ranked ranked = ForYouRanking.rank(pool, reader(trial.random()), Set.of(), NOW);

      eachBlockHoldsItsCaps(trial.at(), ranked.items(), fillers / 20);
    }
  }

  @Test
  void theSeenPartStartsItsOwnBlocksAndHoldsTheSameCaps() {
    for (int t = 0; t < TRIALS; t++) {
      Trial trial = trial(t);
      Random random = trial.random();
      List<ForYouCandidate> unseen = crowded(random, 40 + random.nextInt(100));
      List<ForYouCandidate> pool = new ArrayList<>(unseen);
      int fillers = 20 + random.nextInt(100);
      List<ForYouCandidate> seenPart = new ArrayList<>();
      for (ForYouCandidate c : crowded(random, fillers)) {
        seenPart.add(
            candidate(
                c.order() + 10_000,
                c.author() + "-seen",
                c.host().replace(".example", "-seen.example"),
                c.language(),
                c.tags(),
                Duration.between(c.createdAt(), NOW),
                c.likes(),
                0,
                0));
      }
      pool.addAll(seenPart);
      Set<String> seen = seenPart.stream().map(ForYouCandidate::uri).collect(Collectors.toSet());
      Ranked ranked = ForYouRanking.rank(pool, reader(random), seen, NOW);
      if (ranked.seenFrom() < 0) {
        continue;
      }
      List<ForYouCandidate> after =
          ranked.items().subList(ranked.seenFrom(), ranked.items().size());

      eachBlockHoldsItsCaps(trial.at(), after, fillers / 20);
    }
  }

  @Test
  void oneAuthorCanHoldFourOfTwentyInARowAcrossABlockEdgeSinceTheCapIsPerBlockOfTwenty() {
    List<ForYouCandidate> pool = new ArrayList<>();
    long order = 1;
    for (int i = 0; i < 18; i++) {
      pool.add(
          candidate(
              order++,
              "high" + i,
              "high" + i + ".example",
              "ko",
              Set.of(),
              Duration.ofHours(1),
              1_000,
              0,
              0));
    }
    for (int i = 0; i < 4; i++) {
      pool.add(
          candidate(
              order++, "loud", "loud.example", "ko", Set.of(), Duration.ofHours(1), 100, 0, 0));
    }
    for (int i = 0; i < 18; i++) {
      pool.add(
          candidate(
              order++,
              "low" + i,
              "low" + i + ".example",
              "ko",
              Set.of(),
              Duration.ofHours(1),
              1,
              0,
              0));
    }

    List<String> authors =
        ForYouRanking.rank(pool, ForYouReader.NONE, Set.of(), NOW).items().stream()
            .map(ForYouCandidate::author)
            .toList();

    assertThat(authors.subList(18, 22)).containsOnly("loud");
    eachBlockHoldsItsCaps(
        "edge", ForYouRanking.rank(pool, ForYouReader.NONE, Set.of(), NOW).items(), 2);
  }
}
