package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.federation.domain.topics.Topic;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HeldTopicsTest {

  private static final Instant NOW = Instant.parse("2026-11-18T09:00:00Z");

  private static Topic tag(String name, String... servers) {
    return TopicFixtures.tag(name, servers);
  }

  private static TrendingStatusReader.Read read(String uri) {
    return TopicFixtures.read(uri);
  }

  private static List<String> uris(List<TrendingStatusReader.Read> reads) {
    return reads.stream().map(read -> read.note().uri()).toList();
  }

  @Test
  void aTopicsPostsAreReadOnceAndHeldForTenMinutes() {
    Topic harbor = tag("harbor", "a.example");
    HeldTopics held = new HeldTopics(List.of(harbor));
    AtomicInteger asked = new AtomicInteger();

    for (Instant at :
        List.of(NOW, NOW.plusSeconds(1), NOW.plus(HeldTopics.FRESH), NOW.plusSeconds(601))) {
      held.posts(
          harbor,
          at,
          () -> {
            asked.incrementAndGet();
            return List.of(read("https://a.example/users/someone/statuses/1"));
          });
    }

    assertThat(asked).hasValue(2);
  }

  @Test
  void requestsArrivingTogetherShareOneRead() throws Exception {
    Topic harbor = tag("harbor", "a.example");
    HeldTopics held = new HeldTopics(List.of(harbor));
    AtomicInteger asked = new AtomicInteger();
    CountDownLatch reading = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    List<Future<List<TrendingStatusReader.Read>>> answers = new ArrayList<>();

    try (ExecutorService requests = Executors.newFixedThreadPool(8)) {
      for (int request = 0; request < 8; request++) {
        answers.add(
            requests.submit(
                () ->
                    held.posts(
                        harbor,
                        NOW,
                        () -> {
                          asked.incrementAndGet();
                          reading.countDown();
                          await(release);
                          return List.of(read("https://a.example/users/someone/statuses/1"));
                        })));
      }
      assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();
      Thread.sleep(100);
      release.countDown();
      for (Future<List<TrendingStatusReader.Read>> answer : answers) {
        assertThat(uris(answer.get(5, TimeUnit.SECONDS)))
            .containsExactly("https://a.example/users/someone/statuses/1");
      }
    }

    assertThat(asked).hasValue(1);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void onlyTopicsInThePoolAreEverReadOrHeld() {
    Topic harbor = tag("harbor", "a.example");
    Topic outsider = tag("outsider", "a.example");
    HeldTopics held = new HeldTopics(List.of(harbor));
    AtomicInteger asked = new AtomicInteger();

    List<TrendingStatusReader.Read> answer =
        held.posts(
            outsider,
            NOW,
            () -> {
              asked.incrementAndGet();
              return List.of(read("https://a.example/users/someone/statuses/1"));
            });
    held.seed("tag:outsider", List.of(), NOW);

    assertThat(answer).isEmpty();
    assertThat(asked).hasValue(0);
    assertThat(held.postsHeld()).isZero();
    assertThat(held.find("tag:outsider")).isEmpty();
    assertThat(held.find("tag:harbor")).contains(harbor);
  }

  @Test
  void aSeededTopicAnswersWithoutAReadUntilTheSeedIsTenMinutesOld() {
    Topic harbor = tag("harbor", "a.example");
    HeldTopics held = new HeldTopics(List.of(harbor));
    held.seed("tag:harbor", List.of(read("https://a.example/users/someone/statuses/1")), NOW);
    AtomicInteger asked = new AtomicInteger();

    List<TrendingStatusReader.Read> seeded =
        held.posts(harbor, NOW.plusSeconds(599), () -> List.of());
    List<TrendingStatusReader.Read> later =
        held.posts(
            harbor,
            NOW.plusSeconds(601),
            () -> {
              asked.incrementAndGet();
              return List.of(read("https://a.example/users/someone/statuses/2"));
            });

    assertThat(uris(seeded)).containsExactly("https://a.example/users/someone/statuses/1");
    assertThat(uris(later)).containsExactly("https://a.example/users/someone/statuses/2");
    assertThat(asked).hasValue(1);
  }

  @Test
  void postsOlderThanTenMinutesAreLetGoWhenAnyTopicIsOpened() {
    Topic harbor = tag("harbor", "a.example");
    Topic lantern = tag("lantern", "a.example");
    Topic ferry = tag("ferry", "a.example");
    HeldTopics held = new HeldTopics(List.of(harbor, lantern, ferry));
    held.posts(harbor, NOW, () -> List.of(read("https://a.example/users/someone/statuses/1")));
    held.posts(
        lantern,
        NOW.plusSeconds(300),
        () -> List.of(read("https://a.example/users/someone/statuses/2")));
    assertThat(held.postsHeld()).isEqualTo(2);

    held.posts(
        ferry,
        NOW.plusSeconds(601),
        () -> List.of(read("https://a.example/users/someone/statuses/3")));

    assertThat(held.postsHeld()).isEqualTo(2);
    AtomicInteger asked = new AtomicInteger();
    held.posts(
        lantern,
        NOW.plusSeconds(602),
        () -> {
          asked.incrementAndGet();
          return List.of();
        });
    assertThat(asked).hasValue(0);
  }

  @Test
  void aReadThatBreaksIsNotHeld() {
    Topic harbor = tag("harbor", "a.example");
    HeldTopics held = new HeldTopics(List.of(harbor));

    assertThatThrownBy(
            () ->
                held.posts(
                    harbor,
                    NOW,
                    () -> {
                      throw new IllegalStateException("database away");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(held.postsHeld()).isZero();
    assertThat(
            uris(
                held.posts(
                    harbor,
                    NOW,
                    () -> List.of(read("https://a.example/users/someone/statuses/1")))))
        .hasSize(1);
  }

  @Test
  void aBlockedServerLeavesEveryTopicAndATopicOnlyThereIsGone() {
    Topic harbor = tag("harbor", "bad.example", "a.example");
    Topic lantern = tag("lantern", "sub.bad.example");
    Topic ferry = tag("ferry", "notbad.example");
    HeldTopics held = new HeldTopics(List.of(harbor, lantern, ferry));
    held.seed(
        "tag:harbor",
        List.of(
            read("https://a.example/users/someone/statuses/1"),
            read("https://bad.example/users/someone/statuses/2"),
            read("https://deep.bad.example/users/someone/statuses/3")),
        NOW);
    held.seed("tag:lantern", List.of(read("https://a.example/users/someone/statuses/4")), NOW);

    HeldTopics left = held.without("bad.example");

    assertThat(left.topics()).extracting(Topic::id).containsExactly("tag:harbor", "tag:ferry");
    assertThat(left.find("tag:harbor").orElseThrow().servers()).containsExactly("a.example");
    assertThat(left.find("tag:ferry")).contains(ferry);
    assertThat(left.postsHeld()).isEqualTo(1);
    assertThat(uris(left.posts(left.find("tag:harbor").orElseThrow(), NOW, List::of)))
        .containsExactly("https://a.example/users/someone/statuses/1");
  }
}
