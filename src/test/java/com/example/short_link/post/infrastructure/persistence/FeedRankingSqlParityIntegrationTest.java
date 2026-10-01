package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.feed.FeedCandidate;
import com.example.short_link.post.domain.feed.FeedRanking;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeedRankingSqlParityIntegrationTest {

  private static final List<String> LANGUAGES = List.of("ko", "ja", "en");

  @Autowired private PostRepository postRepository;
  @Autowired private PostViewEventRepository postViewEventRepository;
  @Autowired private SeriesRepository seriesRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager em;

  @Test
  void recentAndTrendingInMemoryMatchTheFeedQueries() {
    Set<Long> fixture = seed(new Random(20261002L));
    em.flush();
    em.clear();

    List<FeedCandidate> pool = postRepository.findFeedCandidates(FeedRanking.CANDIDATE_POOL_SIZE);
    Map<Long, Long> views = viewsSince(Instant.now().minus(FeedRanking.TRENDING_WINDOW));

    List<String> languages = new ArrayList<>(LANGUAGES);
    languages.add(null);
    for (String lang : languages) {
      List<FeedCandidate> scoped =
          pool.stream().filter(c -> lang == null || lang.equals(c.languageTag())).toList();
      assertThat(
              within(
                  fixture, FeedRanking.recent(scoped).stream().map(FeedCandidate::postId).toList()))
          .as("recent lang=%s", lang)
          .hasSizeGreaterThan(3)
          .containsExactlyElementsOf(
              within(fixture, ids(postRepository.findPublishedRecent(lang, 0, 1_000))));
      assertThat(
              within(
                  fixture,
                  FeedRanking.trending(scoped, views).stream().map(FeedCandidate::postId).toList()))
          .as("trending lang=%s", lang)
          .containsExactlyElementsOf(
              within(fixture, ids(postRepository.findPublishedTrending(lang, 0, 1_000))));
    }
  }

  private Set<Long> seed(Random random) {
    List<Long> authors = IntStream.range(0, 4).mapToObj(i -> user("rankparity" + i)).toList();
    List<Long> series =
        IntStream.range(0, 3)
            .mapToObj(
                i ->
                    seriesRepository
                        .save(new SeriesEntity(authors.get(i), "rank-series-" + i, "S" + i))
                        .getId())
            .toList();
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    List<Integer> order = new ArrayList<>(IntStream.range(0, 60).boxed().toList());
    Collections.shuffle(order, random);
    Set<Long> ids = new HashSet<>();
    Map<Integer, Integer> episodes = new HashMap<>();
    for (int i = 0; i < 60; i++) {
      int a = random.nextInt(authors.size());
      PostEntity p =
          new PostEntity(
              authors.get(a), "rank-parity-" + i, "Rank " + i, LANGUAGES.get(random.nextInt(3)));
      ReflectionTestUtils.setField(
          p, "publishedAt", now.minus(Duration.ofMinutes(53L * (order.get(i) + 1))));
      if (random.nextDouble() < 0.15) {
        p.measureBody("짧은 글");
      } else {
        DiscoverableBodies.discoverable(p);
      }
      if (a < series.size() && random.nextDouble() < 0.5) {
        p.assignToSeries(series.get(a), episodes.merge(a, 1, Integer::sum));
      }
      if (random.nextDouble() >= 0.08) {
        p.publish();
      }
      Long id = postRepository.save(p).getId();
      ids.add(id);
      int inWindow = random.nextInt(4) == 0 ? 0 : random.nextInt(6);
      for (int v = 0; v < inWindow; v++) {
        view(id, now.minus(Duration.ofHours(1 + random.nextInt(140))));
      }
      for (int v = 0; v < random.nextInt(4); v++) {
        view(id, now.minus(Duration.ofDays(8 + random.nextInt(20))));
      }
      if (random.nextInt(5) == 0) {
        for (int v = 0; v < 10; v++) {
          postViewEventRepository.save(
              PostViewEventEntity.builder()
                  .postId(id)
                  .viewedAt(now.minus(Duration.ofHours(1 + random.nextInt(140))))
                  .bot(true)
                  .build());
        }
      }
    }
    return ids;
  }

  private Map<Long, Long> viewsSince(Instant since) {
    Map<Long, Long> views = new HashMap<>();
    em.createQuery(
            "select e.postId, count(e) from PostViewEventEntity e "
                + "where e.viewedAt >= :since and e.bot = false group by e.postId",
            Object[].class)
        .setParameter("since", since)
        .getResultList()
        .forEach(row -> views.put((Long) row[0], (Long) row[1]));
    return views;
  }

  private void view(long postId, Instant at) {
    postViewEventRepository.save(new PostViewEventEntity(postId, at));
  }

  private long user(String handle) {
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    return userRepository.save(u).getId();
  }

  private static List<Long> ids(List<PostEntity> posts) {
    return posts.stream().map(PostEntity::getId).toList();
  }

  private static List<Long> within(Collection<Long> fixture, List<Long> ranked) {
    return ranked.stream().filter(fixture::contains).toList();
  }
}
