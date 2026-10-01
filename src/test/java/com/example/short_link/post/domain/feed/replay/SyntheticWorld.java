package com.example.short_link.post.domain.feed.replay;

import com.example.short_link.post.domain.feed.FeedCandidate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class SyntheticWorld {

  static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  static final int DAYS = 120;
  static final int AUTHORS = 60;
  static final int READERS = 400;
  static final List<String> LANGUAGES = List.of("ko", "ja", "en");
  static final Duration POPULARITY_WINDOW = Duration.ofDays(7);

  private static final int TOPICS = 6;
  private static final double[] TOPIC_SHARE = {0.35, 0.15, 0.15, 0.12, 0.13, 0.10};
  private static final double[] AUTHOR_LANGUAGE_SHARE = {0.5, 0.4, 0.1};

  // Index = tag count - 1. The shares are the production corpus of 2026-07 (51 posts).
  private static final double[] TAGS_PER_POST = {0.02, 0.02, 0.25, 0.26, 0.45};

  private static final String[][][] TOPIC_TAGS = {
    {
      {"자바", "스프링", "JPA", "백엔드", "스프링부트", "트랜잭션"},
      {"Java", "Spring", "SpringBoot", "JPA", "Java入門", "バックエンド"},
      {"java", "spring", "jpa", "backend", "hibernate"}
    },
    {
      {"도커", "배포", "AWS", "쿠버네티스", "nginx"},
      {"Docker", "docker-compose", "nginx", "インフラ", "AWS"},
      {"docker", "kubernetes", "nginx", "aws", "devops"}
    },
    {
      {"HTTP", "웹소켓", "네트워크", "프론트엔드", "리액트"},
      {"HTTP", "websocket", "WebFlux", "Web開発", "フロントエンド"},
      {"http", "websocket", "react", "frontend", "web"}
    },
    {
      {"알고리즘", "자료구조", "디자인패턴", "DDD"},
      {"アルゴリズム", "デザインパターン", "設計パターン", "DDD"},
      {"algorithm", "bigo", "design-patterns", "ddd"}
    },
    {
      {"회고", "취업", "신입", "면접"},
      {"転職", "キャリア", "振り返り", "Qiita"},
      {"career", "retrospective", "interview", "github"}
    },
    {
      {"디지털 회로설계", "FPGA", "베릴로그"},
      {"FPGA", "回路設計", "Verilog"},
      {"fpga", "verilog", "hardware"}
    }
  };

  private static final String[][] LEVEL_TAGS = {
    {"입문", "초보자"}, {"初心者", "初心者向け", "初心者エンジニア"}, {"beginner"}
  };

  private static final List<List<String>> READER_LANGUAGES =
      List.of(
          List.of("ko"),
          List.of("ja"),
          List.of("ko", "ja"),
          List.of("ko", "en"),
          List.of("ja", "en"),
          List.of("en"));
  private static final double[] READER_LANGUAGE_SHARE = {0.38, 0.30, 0.10, 0.10, 0.07, 0.05};

  record Post(FeedCandidate candidate, int topic, double quality, boolean discoverable) {
    long id() {
      return candidate.postId();
    }

    long authorId() {
      return candidate.authorId();
    }

    String lang() {
      return candidate.languageTag();
    }

    Instant publishedAt() {
      return candidate.publishedAt();
    }
  }

  record Reader(
      long id,
      List<String> languageOrder,
      Set<String> languages,
      double[] affinity,
      double topAffinity,
      List<String> followedTags,
      List<String> hiddenTags,
      double activity,
      int joinDay) {}

  record Read(Instant at, long readerId, long postId) {}

  record View(Instant at, long postId, boolean bot) {}

  record Like(Instant at, long readerId, long postId) {}

  private record Session(Instant at, Reader reader) {}

  final WorldSpec spec;
  final List<Post> posts;
  final List<Reader> readers;
  final List<Read> reads;
  final List<View> views;
  final List<Like> likes;
  final Map<Long, Post> postById = new HashMap<>();

  private SyntheticWorld(
      WorldSpec spec,
      List<Post> posts,
      List<Reader> readers,
      List<Read> reads,
      List<View> views,
      List<Like> likes) {
    this.spec = spec;
    this.posts = posts;
    this.readers = readers;
    this.reads = reads;
    this.views = views;
    this.likes = likes;
    posts.forEach(p -> postById.put(p.id(), p));
  }

  SyntheticWorld truncatedAt(Instant cut) {
    return new SyntheticWorld(
        spec,
        posts,
        readers,
        reads.stream().filter(r -> r.at().isBefore(cut)).toList(),
        views.stream().filter(v -> v.at().isBefore(cut)).toList(),
        likes.stream().filter(l -> l.at().isBefore(cut)).toList());
  }

  static SyntheticWorld generate(WorldSpec spec, long seed) {
    Random corpus = new Random(seed);
    List<Post> posts = posts(corpus);
    List<Reader> readers = readers(corpus);
    return simulate(spec, posts, readers, new Random(seed * 31 + 17));
  }

  static double ageDays(Post post, Instant now) {
    return (now.getEpochSecond() - post.publishedAt().getEpochSecond()) / 86_400.0;
  }

  private static List<Post> posts(Random random) {
    record Draft(
        long authorId,
        int topic,
        String lang,
        List<String> tags,
        Instant publishedAt,
        Long seriesId,
        double quality,
        boolean discoverable) {}
    List<Draft> drafts = new ArrayList<>();
    long nextSeries = 1;
    for (int a = 0; a < AUTHORS; a++) {
      long authorId = a + 1;
      String lang = LANGUAGES.get(Draw.weighted(random, AUTHOR_LANGUAGE_SHARE));
      int main = Draw.weighted(random, TOPIC_SHARE);
      int second = (main + 1 + random.nextInt(TOPICS - 1)) % TOPICS;
      double rate = 0.0635 * Math.min(15, Draw.pareto(random, 1.6));
      int joinDay = random.nextDouble() < 0.3 ? 0 : random.nextInt(90);
      boolean seriesWriter = random.nextDouble() < 0.3;
      Long openSeries = null;
      int openTopic = 0;
      String openLang = lang;
      int openLength = 0;
      for (int day = joinDay; day < DAYS; day++) {
        int count = Draw.poisson(random, rate);
        for (int k = 0; k < count; k++) {
          Instant at = START.plus(Duration.ofDays(day)).plusSeconds(random.nextInt(86_400));
          int topic = random.nextDouble() < 0.8 ? main : second;
          String postLang = random.nextDouble() < 0.85 ? lang : otherLanguage(random, lang);
          Long series = null;
          if (seriesWriter) {
            if (openSeries != null && openLength < 6 && random.nextDouble() < 0.5) {
              series = openSeries;
              topic = openTopic;
              postLang = openLang;
              openLength++;
            } else if (random.nextDouble() < 0.2) {
              series = nextSeries++;
              openSeries = series;
              openTopic = topic;
              openLang = postLang;
              openLength = 1;
            }
          }
          drafts.add(
              new Draft(
                  authorId,
                  topic,
                  postLang,
                  tags(random, topic, postLang),
                  at,
                  series,
                  Draw.logNormal(random, 0, 0.6),
                  random.nextDouble() >= 0.07));
        }
      }
    }
    drafts.sort(Comparator.comparing(Draft::publishedAt).thenComparingLong(Draft::authorId));
    List<Post> posts = new ArrayList<>();
    for (int i = 0; i < drafts.size(); i++) {
      Draft d = drafts.get(i);
      FeedCandidate candidate =
          new FeedCandidate(i + 1, d.authorId(), d.tags(), d.lang(), d.publishedAt(), d.seriesId());
      posts.add(new Post(candidate, d.topic(), d.quality(), d.discoverable()));
    }
    return List.copyOf(posts);
  }

  private static List<String> tags(Random random, int topic, String lang) {
    int language = LANGUAGES.indexOf(lang);
    int count = 1 + Draw.weighted(random, TAGS_PER_POST);
    List<String> tags = new ArrayList<>();
    if (random.nextDouble() < 0.4) {
      String[] level = LEVEL_TAGS[language];
      tags.add(level[random.nextInt(level.length)]);
    }
    tags.addAll(Draw.sample(random, List.of(TOPIC_TAGS[topic][language]), count - tags.size()));
    return List.copyOf(tags);
  }

  private static String otherLanguage(Random random, String lang) {
    List<String> others = LANGUAGES.stream().filter(l -> !l.equals(lang)).toList();
    return others.get(random.nextInt(others.size()));
  }

  private static List<Reader> readers(Random random) {
    List<Reader> readers = new ArrayList<>();
    for (int i = 0; i < READERS; i++) {
      List<String> languageOrder =
          READER_LANGUAGES.get(Draw.weighted(random, READER_LANGUAGE_SHARE));
      int locale = LANGUAGES.indexOf(languageOrder.get(0));
      double[] affinity = Draw.dirichlet(random, 0.5, TOPICS);
      int top = 0;
      int bottom = 0;
      for (int t = 1; t < TOPICS; t++) {
        if (affinity[t] > affinity[top]) top = t;
        if (affinity[t] < affinity[bottom]) bottom = t;
      }
      List<String> followed =
          random.nextDouble() < 0.35
              ? Draw.sample(random, List.of(TOPIC_TAGS[top][locale]), 1 + random.nextInt(3))
              : List.of();
      String[] dislikedTags = TOPIC_TAGS[bottom][locale];
      List<String> hidden =
          random.nextDouble() < 0.08
              ? List.of(dislikedTags[random.nextInt(dislikedTags.length)])
              : List.of();
      readers.add(
          new Reader(
              10_001 + i,
              languageOrder,
              Set.copyOf(languageOrder),
              affinity,
              affinity[top],
              followed,
              hidden,
              Draw.logNormal(random, StrictMath.log(0.5), 0.9),
              random.nextDouble() < 0.25 ? 0 : random.nextInt(100)));
    }
    return List.copyOf(readers);
  }

  private static SyntheticWorld simulate(
      WorldSpec spec, List<Post> posts, List<Reader> readers, Random random) {
    List<Read> reads = new ArrayList<>();
    List<View> views = new ArrayList<>();
    List<Like> likes = new ArrayList<>();
    ReadingState state = new ReadingState();
    List<Post> pool = new ArrayList<>();
    int nextPost = 0;
    for (int day = 0; day < DAYS; day++) {
      Instant dayStart = START.plus(Duration.ofDays(day));
      List<Session> sessions = new ArrayList<>();
      for (Reader reader : readers) {
        if (reader.joinDay() > day) continue;
        int count = Draw.poisson(random, reader.activity());
        for (int k = 0; k < count; k++) {
          sessions.add(new Session(dayStart.plusSeconds(random.nextInt(86_400)), reader));
        }
      }
      sessions.sort(Comparator.comparing(Session::at).thenComparingLong(s -> s.reader().id()));
      for (Session session : sessions) {
        while (nextPost < posts.size()
            && !posts.get(nextPost).publishedAt().isAfter(session.at())) {
          Post post = posts.get(nextPost++);
          if (post.discoverable()) pool.add(post);
        }
        state.forgetBefore(session.at().minus(POPULARITY_WINDOW));
        double[] weights = state.choiceWeights(spec, session.reader(), pool, session.at());
        if (weights == null) continue;
        Post chosen = pool.get(Draw.weighted(random, weights));
        Read read = new Read(session.at(), session.reader().id(), chosen.id());
        reads.add(read);
        views.add(new View(session.at(), chosen.id(), false));
        state.record(read, chosen);
        if (random.nextDouble() < Math.min(0.3, 0.06 * chosen.quality())) {
          likes.add(new Like(session.at(), session.reader().id(), chosen.id()));
        }
      }
      visitorsAndCrawlers(random, posts, dayStart, views);
    }
    views.sort(Comparator.comparing(View::at).thenComparingLong(View::postId));
    return new SyntheticWorld(
        spec, posts, readers, List.copyOf(reads), List.copyOf(views), List.copyOf(likes));
  }

  private static void visitorsAndCrawlers(
      Random random, List<Post> posts, Instant dayStart, List<View> views) {
    Instant dayEnd = dayStart.plus(Duration.ofDays(1));
    for (Post post : posts) {
      if (!post.publishedAt().isBefore(dayEnd)) break;
      if (!post.discoverable()) continue;
      Instant from = post.publishedAt().isAfter(dayStart) ? post.publishedAt() : dayStart;
      long span = Duration.between(from, dayEnd).toSeconds();
      if (span <= 0) continue;
      double age = ageDays(post, dayEnd);
      int visitors = Draw.poisson(random, 1.5 * post.quality() * StrictMath.exp(-age / 7));
      for (int v = 0; v < visitors; v++) {
        views.add(new View(from.plusSeconds(random.nextInt((int) span)), post.id(), false));
      }
      if (random.nextDouble() < 0.3) {
        views.add(new View(from.plusSeconds(random.nextInt((int) span)), post.id(), true));
      }
      if (random.nextDouble() < 0.002) {
        Instant burst = from.plusSeconds(random.nextInt((int) span));
        for (int v = 10 + random.nextInt(21); v > 0; v--) {
          views.add(new View(burst.plusSeconds(random.nextInt(3_600)), post.id(), true));
        }
      }
    }
  }

  // The replay keeps its own copy in step with the events, so the ceiling scores exactly what the
  // simulation sampled from.
  static final class ReadingState {
    private final Map<Long, Set<Long>> readByReader = new HashMap<>();
    private final Map<Long, Map<Long, Integer>> authorReadsByReader = new HashMap<>();
    private final ArrayDeque<Read> window = new ArrayDeque<>();
    private final Map<Long, Integer> windowReads = new HashMap<>();

    void forgetBefore(Instant cutoff) {
      while (!window.isEmpty() && window.peekFirst().at().isBefore(cutoff)) {
        windowReads.merge(window.pollFirst().postId(), -1, Integer::sum);
      }
    }

    void record(Read read, Post post) {
      readByReader.computeIfAbsent(read.readerId(), id -> new HashSet<>()).add(post.id());
      authorReadsByReader
          .computeIfAbsent(read.readerId(), id -> new HashMap<>())
          .merge(post.authorId(), 1, Integer::sum);
      window.addLast(read);
      windowReads.merge(post.id(), 1, Integer::sum);
    }

    boolean hasRead(long readerId, long postId) {
      return readByReader.getOrDefault(readerId, Set.of()).contains(postId);
    }

    double logit(WorldSpec spec, Reader reader, Post post, Instant now) {
      return spec.logit(
          reader,
          post,
          ageDays(post, now),
          authorReadsByReader.getOrDefault(reader.id(), Map.of()).getOrDefault(post.authorId(), 0),
          windowReads.getOrDefault(post.id(), 0));
    }

    double[] choiceWeights(WorldSpec spec, Reader reader, List<Post> pool, Instant now) {
      double[] logits = new double[pool.size()];
      double max = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < pool.size(); i++) {
        Post post = pool.get(i);
        logits[i] =
            hasRead(reader.id(), post.id())
                ? Double.NEGATIVE_INFINITY
                : logit(spec, reader, post, now);
        max = Math.max(max, logits[i]);
      }
      if (max == Double.NEGATIVE_INFINITY) return null;
      double[] weights = new double[pool.size()];
      for (int i = 0; i < pool.size(); i++) {
        weights[i] = StrictMath.exp(logits[i] - max);
      }
      return weights;
    }
  }
}
