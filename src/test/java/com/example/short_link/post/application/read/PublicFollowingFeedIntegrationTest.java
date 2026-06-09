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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The following feed merges followed authors' posts with subscribed series' posts. Only the real
 * union query (with the no-match sentinel when a side is empty) against MySQL proves the OR + IN
 * behavior, so this drives feedFollowing end-to-end.
 */
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

  @Test
  void mergesFollowedAuthorPostsAndSubscribedSeriesPosts() {
    long me = user("reader");
    long followed = user("followee");
    long stranger = user("stranger");

    // I follow `followed`; I subscribe to `stranger`'s series (but don't follow them).
    followRepository.save(new FollowEntity(me, followed));
    long series = seriesRepository.save(new SeriesEntity(stranger, "guide", "Guide")).getId();
    subscriptionRepository.insertIgnore(me, series);

    publish(followed, "from-followed", null);
    publish(stranger, "series-episode", series);
    publish(stranger, "stranger-standalone", null); // not followed, not in a subscribed series

    List<String> slugs =
        service.feedFollowing(me, 0, 20).items().stream().map(PublicFeedItem::slug).toList();

    assertThat(slugs).contains("from-followed", "series-episode");
    assertThat(slugs).doesNotContain("stranger-standalone");
  }

  @Test
  void mergesPostsCarryingAFollowedTag() {
    long me = user("topicreader");
    long author = user("writer");

    // I follow the topic "Spring" (mixed case) — but not the author.
    tagPrefRepository.save(new UserTagPrefEntity(me, "Spring", TagPrefKind.FOLLOW));

    publish(author, "spring-post", null, List.of("spring", "java")); // matches by tag (case-insens)
    publish(author, "react-post", null, List.of("react")); // unrelated topic, author not followed

    List<String> slugs =
        service.feedFollowing(me, 0, 20).items().stream().map(PublicFeedItem::slug).toList();

    assertThat(slugs).contains("spring-post");
    assertThat(slugs).doesNotContain("react-post");
  }

  @Test
  void emptyWhenFollowingAndSubscribingNothing() {
    long me = user("lonely");
    assertThat(service.feedFollowing(me, 0, 20).items()).isEmpty();
  }
}
