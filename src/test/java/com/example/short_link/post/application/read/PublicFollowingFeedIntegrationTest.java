package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.TagPrefKind;
import com.example.short_link.post.domain.UserTagPrefEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import com.example.short_link.post.domain.repository.UserTagPrefRepository;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

// The following feed merges followed authors' posts with subscribed series' posts. Only the real
// union query (with the no-match sentinel when a side is empty) against MySQL proves the OR + IN
// behavior, so this drives feedFollowing end-to-end.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PublicFollowingFeedIntegrationTest {

  @Autowired private PublicFeedQueryService service;
  @Autowired private PostRepository postRepository;
  @Autowired private SeriesRepository seriesRepository;
  @Autowired private SeriesSubscriptionRepository subscriptionRepository;
  @Autowired private FollowRepository followRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserTagPrefRepository tagPrefRepository;

  private long user(String handle) {
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    userRepository.save(u);
    return u.getId();
  }

  private String publish(long userId, String slug, Long seriesId) {
    return publish(userId, slug, seriesId, List.of());
  }

  private String publish(long userId, String slug, Long seriesId, List<String> tags) {
    PostEntity p = new PostEntity(userId, slug, slug, "ko");
    if (seriesId != null) p.assignToSeries(seriesId, 0);
    if (!tags.isEmpty()) p.updateTags(tags);
    p.publish();
    postRepository.save(p);
    return slug;
  }

  private void publishAt(long userId, String slug, Instant at) {
    PostEntity p = new PostEntity(userId, slug, slug, "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "publishedAt", at);
    postRepository.save(p);
  }

  @Test
  void mergesFollowedAuthorPostsAndSubscribedSeriesPosts() {
    long me = user("reader");
    long followed = user("followee");
    long stranger = user("stranger");

    followRepository.save(new FollowEntity(me, followed));
    long series = seriesRepository.save(new SeriesEntity(stranger, "guide", "Guide")).getId();
    subscriptionRepository.insertIgnore(me, series);

    publish(followed, "from-followed", null);
    publish(stranger, "series-episode", series);
    publish(stranger, "stranger-standalone", null);

    List<PublicFeedItem> items = service.feedFollowing(me, 0, 20).items();
    List<String> slugs = items.stream().map(PublicFeedItem::slug).toList();

    assertThat(slugs).contains("from-followed", "series-episode");
    assertThat(slugs).doesNotContain("stranger-standalone");
    assertThat(reasonOf(items, "from-followed").kind()).isEqualTo(FollowReason.AUTHOR);
    assertThat(reasonOf(items, "series-episode").kind()).isEqualTo(FollowReason.SERIES);
  }

  private static FollowReason reasonOf(List<PublicFeedItem> items, String slug) {
    return items.stream()
        .filter(i -> i.slug().equals(slug))
        .findFirst()
        .orElseThrow()
        .followReason();
  }

  @Test
  void mergesPostsCarryingAFollowedTag() {
    long me = user("topicreader");
    long author = user("writer");

    tagPrefRepository.save(new UserTagPrefEntity(me, "Spring", TagPrefKind.FOLLOW));

    publish(author, "spring-post", null, List.of("spring", "java"));
    publish(author, "react-post", null, List.of("react"));

    List<PublicFeedItem> items = service.feedFollowing(me, 0, 20).items();
    List<String> slugs = items.stream().map(PublicFeedItem::slug).toList();

    assertThat(slugs).contains("spring-post");
    assertThat(slugs).doesNotContain("react-post");
    FollowReason reason = reasonOf(items, "spring-post");
    assertThat(reason.kind()).isEqualTo(FollowReason.TOPIC);
    assertThat(reason.tag()).isEqualTo("spring");
  }

  @Test
  void followingOnlyAuthorsListsTheirPostsNewestFirstAndCountsThem() {
    long me = user("authorsonly");
    long first = user("firstauthor");
    long second = user("secondauthor");
    long stranger = user("notfollowed");

    followRepository.save(new FollowEntity(me, first));
    followRepository.save(new FollowEntity(me, second));

    Instant base = Instant.parse("2026-10-01T00:00:00Z");
    publishAt(first, "first-old", base);
    publishAt(stranger, "stranger-post", base.plusSeconds(60));
    publishAt(second, "second-mid", base.plusSeconds(120));
    publishAt(first, "first-new", base.plusSeconds(180));

    PublicFeedView page = service.feedFollowing(me, 0, 2);
    assertThat(page.items())
        .extracting(PublicFeedItem::slug)
        .containsExactly("first-new", "second-mid");
    assertThat(page.hasNext()).isTrue();
    assertThat(service.feedFollowing(me, 1, 2).items())
        .extracting(PublicFeedItem::slug)
        .containsExactly("first-old");
    assertThat(service.followingAt(me, 0, 20).items())
        .extracting(at -> at.post().item().slug())
        .containsExactly("first-new", "second-mid", "first-old");
  }

  @Test
  void emptyWhenFollowingAndSubscribingNothing() {
    long me = user("lonely");
    assertThat(service.feedFollowing(me, 0, 20).items()).isEmpty();
  }
}
