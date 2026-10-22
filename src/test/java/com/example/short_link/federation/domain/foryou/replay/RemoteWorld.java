package com.example.short_link.federation.domain.foryou.replay;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// A synthetic fediverse seen from kurl. Four chosen servers post into the latest pull, two big
// servers reach the pool only when a post of theirs trends, and kurl readers pick what to read from
// the last three days by the world's own taste. Every day draws from its own seeded Random, so a
// world cut short is exactly the start of the longer one.
final class RemoteWorld {

  static final Instant START = Instant.parse("2026-09-01T00:00:00Z");
  static final Duration WINDOW = Duration.ofHours(72);
  static final Duration REACTION_PACE = Duration.ofHours(6);
  static final Duration TREND_REFRESH = Duration.ofMinutes(30);
  static final long TRENDING_AT = 40;
  static final String[] LANGUAGES = {"ko", "ja", "en"};
  static final int TOPICS = 12;
  static final int READERS = 300;

  record Host(
      String name,
      boolean chosen,
      double postsPerDay,
      double audience,
      double[] languages,
      int authors,
      double skew) {}

  // Post shares of the chosen servers follow what was kept on 10-22 (silicon 126, planet 51,
  // qdon 36, mstdn.jp 27); trending servers only matter through what crosses TRENDING_AT.
  static final List<Host> HOSTS =
      List.of(
          new Host("social.silicon.moe", true, 90, 1.0, new double[] {0.96, 0.03, 0.01}, 20, 1.9),
          new Host("planet.moe", true, 32, 0.8, new double[] {0.95, 0.04, 0.01}, 20, 1.3),
          new Host("qdon.space", true, 22, 0.6, new double[] {0.96, 0.03, 0.01}, 15, 1.3),
          new Host("mstdn.jp", true, 12, 1.4, new double[] {0.03, 0.96, 0.01}, 20, 1.2),
          new Host("mastodon.social", false, 400, 8.0, new double[] {0.12, 0.18, 0.70}, 300, 1.0),
          new Host("fedibird.com", false, 150, 3.0, new double[] {0.25, 0.72, 0.03}, 150, 1.0));

  record Post(
      long id,
      int host,
      String author,
      String language,
      int topic,
      Set<String> tags,
      Instant createdAt,
      Instant keptAt,
      double quality,
      long finalReach,
      boolean trended) {

    long reachAt(Instant at) {
      long age = Duration.between(createdAt, at).toSeconds();
      if (age <= 0) {
        return 0;
      }
      double share = 1 - StrictMath.exp(-(double) age / REACTION_PACE.toSeconds());
      return (long) StrictMath.floor(finalReach * share);
    }
  }

  record Reader(
      long id, String locale, Set<String> readable, double[] affinity, Set<String> followed) {}

  record Read(
      long seq,
      Instant at,
      long readerId,
      long postId,
      boolean dwelt,
      boolean liked,
      boolean bookmarked,
      boolean answered) {}

  final WorldSpec spec;
  final List<Post> posts = new ArrayList<>();
  final List<Reader> readers = new ArrayList<>();
  final List<Read> reads = new ArrayList<>();
  final Map<Long, Post> byId = new HashMap<>();
  final int days;

  private final List<List<String>> authors = new ArrayList<>();
  private final List<double[]> authorWeights = new ArrayList<>();
  private final Map<Long, List<Read>> readsByReader = new HashMap<>();
  private final Map<Long, Set<Long>> readIds = new HashMap<>();

  private RemoteWorld(WorldSpec spec, int days) {
    this.spec = spec;
    this.days = days;
  }

  static RemoteWorld generate(WorldSpec spec, int days, long seed) {
    RemoteWorld world = new RemoteWorld(spec, days);
    Random people = new Random(seed);
    world.authors();
    world.readers(people);
    for (int day = 0; day < days; day++) {
      Random random = new Random(seed * 1_000_003L + day);
      world.postsOf(day, random);
      world.readsOf(day, random);
    }
    return world;
  }

  private void authors() {
    for (int h = 0; h < HOSTS.size(); h++) {
      Host host = HOSTS.get(h);
      List<String> names = new ArrayList<>();
      double[] weights = new double[host.authors()];
      for (int a = 0; a < host.authors(); a++) {
        names.add(host.name() + "/a" + a);
        weights[a] = 1 / StrictMath.pow(a + 1, host.skew());
      }
      authors.add(names);
      authorWeights.add(weights);
    }
  }

  private void readers(Random random) {
    List<String> followable = new ArrayList<>();
    for (int h = 0; h < 4; h++) {
      followable.addAll(authors.get(h).subList(0, 8));
    }
    for (int r = 0; r < READERS; r++) {
      double roll = random.nextDouble();
      String locale = roll >= 0.85 && roll < 0.95 ? "ja" : "ko";
      Set<String> readable = new LinkedHashSet<>(List.of(locale));
      if (roll >= 0.95) {
        readable.add("ja");
      }
      if (random.nextDouble() < 0.15) {
        readable.add("en");
      }
      double[] affinity = new double[TOPICS];
      for (int t = 0; t < TOPICS; t++) {
        affinity[t] = 0.1 * random.nextDouble();
      }
      int strong = 2 + random.nextInt(2);
      for (int s = 0; s < strong; s++) {
        affinity[random.nextInt(TOPICS)] = 0.6 + 0.4 * random.nextDouble();
      }
      Set<String> followed = new LinkedHashSet<>();
      int follows = random.nextInt(4);
      for (int f = 0; f < follows; f++) {
        followed.add(followable.get(random.nextInt(followable.size())));
      }
      readers.add(new Reader(r + 1, locale, readable, affinity, followed));
    }
  }

  private void postsOf(int day, Random random) {
    Instant dayStart = START.plus(Duration.ofDays(day));
    List<Post> fresh = new ArrayList<>();
    for (int h = 0; h < HOSTS.size(); h++) {
      Host host = HOSTS.get(h);
      int count = poisson(random, host.postsPerDay());
      for (int i = 0; i < count; i++) {
        int author = pick(random, authorWeights.get(h));
        String language = LANGUAGES[pick(random, host.languages())];
        int topic = random.nextInt(TOPICS);
        Set<String> tags = new LinkedHashSet<>();
        if (random.nextDouble() < 0.3) {
          tags.add("t" + topic + "-" + language);
          if (random.nextDouble() < 0.4) {
            tags.add("t" + topic);
          }
        }
        Instant created = dayStart.plusSeconds(random.nextInt(86_400));
        double quality = StrictMath.exp(0.8 * gaussian(random));
        double fame = 1 + 3.0 / (author + 1);
        long finalReach = poisson(random, 4 * quality * host.audience() * fame);
        Instant kept;
        boolean trended = false;
        if (host.chosen()) {
          kept = created.plusSeconds(random.nextInt(300));
        } else if (finalReach >= 2 * TRENDING_AT) {
          kept = trendingSince(created, finalReach);
          trended = true;
        } else {
          continue;
        }
        fresh.add(
            new Post(
                0,
                h,
                authors.get(h).get(author),
                language,
                topic,
                Collections.unmodifiableSet(tags),
                created,
                kept,
                quality,
                finalReach,
                trended));
      }
    }
    fresh.sort(Comparator.comparing(Post::keptAt).thenComparing(Post::author));
    for (Post post : fresh) {
      Post numbered =
          new Post(
              posts.size() + 1L,
              post.host(),
              post.author(),
              post.language(),
              post.topic(),
              post.tags(),
              post.createdAt(),
              post.keptAt(),
              post.quality(),
              post.finalReach(),
              post.trended());
      posts.add(numbered);
      byId.put(numbered.id(), numbered);
    }
  }

  // A big server's post is listed as trending once its count passes TRENDING_AT, seen at the next
  // half-hourly refresh.
  private static Instant trendingSince(Instant created, long finalReach) {
    double share = (double) TRENDING_AT / finalReach;
    long seconds =
        (long) StrictMath.ceil(-StrictMath.log(1 - share) * REACTION_PACE.toSeconds()) + 1;
    long refresh = TREND_REFRESH.toSeconds();
    return created.plusSeconds(((seconds + refresh - 1) / refresh) * refresh);
  }

  List<Post> pool(Instant at, Reader reader) {
    Instant since = at.minus(WINDOW);
    List<Post> pool = new ArrayList<>();
    for (Post post : posts) {
      if (!post.keptAt().isAfter(at)
          && !post.createdAt().isBefore(since)
          && !reader.followed().contains(post.author())) {
        pool.add(post);
      }
    }
    return pool;
  }

  private void readsOf(int day, Random random) {
    Instant dayStart = START.plus(Duration.ofDays(day));
    List<long[]> sessions = new ArrayList<>();
    for (Reader reader : readers) {
      double appetite = 0.4 + 2.5 * random.nextDouble() * random.nextDouble();
      int count = poisson(random, appetite);
      for (int i = 0; i < count; i++) {
        sessions.add(new long[] {random.nextInt(86_400), reader.id()});
      }
    }
    sessions.sort(Comparator.<long[]>comparingLong(s -> s[0]).thenComparingLong(s -> s[1]));
    for (long[] session : sessions) {
      Instant at = dayStart.plusSeconds(session[0]);
      Reader reader = readers.get((int) session[1] - 1);
      List<Post> pool = pool(at, reader);
      Set<Long> already = readIds.getOrDefault(reader.id(), Set.of());
      Map<String, Integer> byAuthor = authorReads(reader.id());
      double[] weights = new double[pool.size()];
      double total = 0;
      for (int i = 0; i < pool.size(); i++) {
        Post post = pool.get(i);
        if (already.contains(post.id())) {
          continue;
        }
        weights[i] =
            StrictMath.exp(spec.logit(reader, post, at, byAuthor.getOrDefault(post.author(), 0)));
        total += weights[i];
      }
      if (total == 0) {
        continue;
      }
      double roll = random.nextDouble() * total;
      int chosen = -1;
      for (int i = 0; i < weights.length && roll > 0; i++) {
        if (weights[i] > 0) {
          roll -= weights[i];
          chosen = i;
        }
      }
      Post post = pool.get(chosen);
      double care = StrictMath.min(1, reader.affinity()[post.topic()] + 0.2 * post.quality());
      Read read =
          new Read(
              reads.size() + 1L,
              at,
              reader.id(),
              post.id(),
              random.nextDouble() < 0.3 + 0.5 * care,
              random.nextDouble() < 0.1 + 0.3 * care,
              random.nextDouble() < 0.03 + 0.1 * care,
              random.nextDouble() < 0.01 + 0.04 * care);
      reads.add(read);
      readsByReader.computeIfAbsent(reader.id(), id -> new ArrayList<>()).add(read);
      readIds.computeIfAbsent(reader.id(), id -> new HashSet<>()).add(post.id());
    }
  }

  private Map<String, Integer> authorReads(long readerId) {
    Map<String, Integer> counts = new HashMap<>();
    for (Read read : readsByReader.getOrDefault(readerId, List.of())) {
      counts.merge(byId.get(read.postId()).author(), 1, Integer::sum);
    }
    return counts;
  }

  static int pick(Random random, double[] weights) {
    double total = 0;
    for (double weight : weights) {
      total += weight;
    }
    double roll = random.nextDouble() * total;
    for (int i = 0; i < weights.length; i++) {
      roll -= weights[i];
      if (roll <= 0) {
        return i;
      }
    }
    return weights.length - 1;
  }

  static int poisson(Random random, double mean) {
    if (mean <= 0) {
      return 0;
    }
    if (mean > 30) {
      return (int)
          StrictMath.max(0, StrictMath.round(mean + StrictMath.sqrt(mean) * gaussian(random)));
    }
    double limit = StrictMath.exp(-mean);
    double product = random.nextDouble();
    int count = 0;
    while (product > limit) {
      product *= random.nextDouble();
      count++;
    }
    return count;
  }

  static double gaussian(Random random) {
    double u = 1 - random.nextDouble();
    double v = random.nextDouble();
    return StrictMath.sqrt(-2 * StrictMath.log(u)) * StrictMath.cos(2 * StrictMath.PI * v);
  }
}
