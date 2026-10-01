package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.DiscoveryQuality;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostLikeEntity;
import com.example.short_link.post.domain.PostReadEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.TagPrefKind;
import com.example.short_link.post.domain.UserTagPrefEntity;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostReadRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.domain.repository.UserTagPrefRepository;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ForYouFeedParityIntegrationTest {

  private static final String FORMER_CANDIDATES =
      "select distinct p from PostEntity p join p.tags t "
          + "where p.status = :status and p.userId <> :userId "
          + "and lower(t) in :tags and p.id not in :excludeIds "
          + "and p.bodyTextLength >= :minBody "
          + "order by p.publishedAt desc";

  private static final String FORMER_COUNT =
      "select count(distinct p) from PostEntity p join p.tags t "
          + "where p.status = :status and p.userId <> :userId "
          + "and lower(t) in :tags and p.id not in :excludeIds "
          + "and p.bodyTextLength >= :minBody";

  private static final List<String> VOCABULARY =
      List.of(
          "pa-java",
          "PA-Java",
          "Pa-Spring",
          "pa-spring",
          "pa-docker",
          "pa-テスト",
          "pa-初心者",
          "PA-AI",
          "pa-ai",
          "pa-jpa",
          "pa-nginx",
          "pa-회고");

  private static final Instant BASE = Instant.parse("2026-07-01T00:00:00Z");

  @Autowired private ForYouQueryService service;
  @Autowired private TagPrefQueryService tagPrefQueryService;
  @Autowired private PostRepository postRepository;
  @Autowired private PostReadRepository postReadRepository;
  @Autowired private PostLikeRepository postLikeRepository;
  @Autowired private UserTagPrefRepository userTagPrefRepository;
  @Autowired private SeriesRepository seriesRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager em;

  @Test
  void everyViewerAndPageMatchesTheFormerCandidateQuery() {
    Random random = new Random(20261001L);
    List<Long> users = IntStream.range(0, 6).mapToObj(i -> user("parity" + i)).toList();
    List<Long> published = seedPosts(random, users);
    for (int i = 0; i < users.size(); i++) {
      seedSignals(random, users.get(i), i, published);
    }
    em.flush();
    em.clear();

    int compared = 0;
    for (Long viewer : users) {
      Set<String> interest = formerInterest(viewer);
      if (interest.isEmpty()) {
        assertThat(ids(service.feedForYou(viewer, 0, 20)))
            .containsExactlyElementsOf(
                postRepository.findPublishedTrending(null, 0, 20).stream()
                    .map(PostEntity::getId)
                    .toList());
        continue;
      }
      List<Long> excluded = recentReadIds(viewer);
      long total = formerCount(viewer, interest, excluded);
      assertThat(total).as("viewer %d has candidates", viewer).isPositive();
      for (int size : List.of(7, 20)) {
        for (int page = 0; (long) page * size <= total; page++) {
          PublicFeedView view = service.feedForYou(viewer, page, size);
          assertThat(ids(view))
              .as("viewer %d page %d size %d", viewer, page, size)
              .containsExactlyElementsOf(formerCandidates(viewer, interest, excluded, page, size));
          assertThat(view.hasNext()).isEqualTo((long) (page + 1) * size < total);
          compared++;
        }
      }
    }
    assertThat(compared).isGreaterThan(20);
  }

  private List<Long> seedPosts(Random random, List<Long> users) {
    Long series =
        seriesRepository.save(new SeriesEntity(users.get(0), "parity-series", "S")).getId();
    List<Integer> order = new ArrayList<>(IntStream.range(0, 70).boxed().toList());
    Collections.shuffle(order, random);
    List<Long> published = new ArrayList<>();
    int episode = 0;
    for (int i = 0; i < 70; i++) {
      long author = users.get(random.nextInt(users.size()));
      PostEntity p =
          new PostEntity(
              author, "parity-" + i, "Parity " + i, List.of("ko", "ja", "en").get(i % 3));
      p.updateTags(sample(random, VOCABULARY, 1 + random.nextInt(4)));
      ReflectionTestUtils.setField(
          p, "publishedAt", BASE.plus(Duration.ofMinutes(37L * order.get(i))));
      if (random.nextDouble() < 0.15) {
        p.measureBody("짧은 글");
      } else {
        DiscoverableBodies.discoverable(p);
      }
      if (author == users.get(0) && i % 3 == 0) {
        p.assignToSeries(series, episode++);
      }
      double status = random.nextDouble();
      if (status >= 0.08) {
        p.publish();
        if (status < 0.14) {
          p.unpublish();
        }
      }
      PostEntity saved = postRepository.save(p);
      if (saved.getStatus() == PostStatus.PUBLISHED) {
        published.add(saved.getId());
      }
    }
    return published;
  }

  private void seedSignals(Random random, long viewer, int index, List<Long> published) {
    if (index == 5) {
      return;
    }
    if (index % 2 == 0) {
      for (String tag : sample(random, VOCABULARY, 1 + random.nextInt(2))) {
        userTagPrefRepository.save(new UserTagPrefEntity(viewer, tag, TagPrefKind.FOLLOW));
      }
    }
    if (index == 1 || index == 3) {
      String hidden = VOCABULARY.get(random.nextInt(VOCABULARY.size()));
      userTagPrefRepository.save(new UserTagPrefEntity(viewer, hidden, TagPrefKind.HIDE));
    }
    for (Long postId : sample(random, published, 3 + random.nextInt(13))) {
      Instant readAt = BASE.plus(Duration.ofHours(random.nextInt(2_000)));
      postReadRepository.save(new PostReadEntity(viewer, postId, readAt));
    }
    for (Long postId : sample(random, published, random.nextInt(5))) {
      postLikeRepository.insertIgnore(postId, viewer);
    }
  }

  private Set<String> formerInterest(long viewer) {
    TagPrefsView prefs = tagPrefQueryService.get(viewer);
    List<Long> signalIds =
        Stream.concat(
                recentReadIds(viewer).stream().limit(40),
                postLikeRepository.findAllByUserIdOrderByCreatedAtDesc(viewer).stream()
                    .map(PostLikeEntity::getPostId)
                    .limit(40))
            .distinct()
            .toList();
    Set<String> interest = new HashSet<>();
    prefs.followed().forEach(t -> interest.add(t.toLowerCase(Locale.ROOT)));
    if (!signalIds.isEmpty()) {
      postRepository
          .findAllByIdIn(signalIds)
          .forEach(p -> p.getTags().forEach(t -> interest.add(t.toLowerCase(Locale.ROOT))));
    }
    prefs.hidden().forEach(t -> interest.remove(t.toLowerCase(Locale.ROOT)));
    assertThat(interest).hasSizeLessThanOrEqualTo(12);
    return interest;
  }

  private List<Long> recentReadIds(long viewer) {
    return postReadRepository.findByUserIdOrderByReadAtDesc(viewer, 0, 200).stream()
        .map(PostReadEntity::getPostId)
        .toList();
  }

  private List<Long> formerCandidates(
      long viewer, Set<String> interest, List<Long> excluded, int page, int size) {
    return em
        .createQuery(FORMER_CANDIDATES, PostEntity.class)
        .setParameter("status", PostStatus.PUBLISHED)
        .setParameter("userId", viewer)
        .setParameter("tags", interest)
        .setParameter("excludeIds", excluded.isEmpty() ? List.of(-1L) : excluded)
        .setParameter("minBody", DiscoveryQuality.MIN_BODY_TEXT_LENGTH)
        .setFirstResult(page * size)
        .setMaxResults(size)
        .getResultList()
        .stream()
        .map(PostEntity::getId)
        .toList();
  }

  private long formerCount(long viewer, Set<String> interest, List<Long> excluded) {
    return em.createQuery(FORMER_COUNT, Long.class)
        .setParameter("status", PostStatus.PUBLISHED)
        .setParameter("userId", viewer)
        .setParameter("tags", interest)
        .setParameter("excludeIds", excluded.isEmpty() ? List.of(-1L) : excluded)
        .setParameter("minBody", DiscoveryQuality.MIN_BODY_TEXT_LENGTH)
        .getSingleResult();
  }

  private long user(String handle) {
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    return userRepository.save(u).getId();
  }

  private static <T> List<T> sample(Random random, List<T> from, int count) {
    List<T> copy = new ArrayList<>(from);
    Collections.shuffle(copy, random);
    return copy.subList(0, Math.min(count, copy.size()));
  }

  private static List<Long> ids(PublicFeedView view) {
    return view.items().stream().map(PublicFeedItem::id).toList();
  }
}
