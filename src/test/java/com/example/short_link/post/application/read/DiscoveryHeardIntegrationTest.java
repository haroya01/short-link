package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteVisibility;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.TagPrefKind;
import com.example.short_link.post.domain.UserTagPrefEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesItemRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import com.example.short_link.post.domain.repository.UserTagPrefRepository;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserMuteEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DiscoveryHeardIntegrationTest {

  private static final String TOPIC = "hrdtopic";
  private static final List<String> HEARD = List.of("hrd-normal", "hrd-expired");
  private static final List<String> UNHEARD =
      List.of("hrd-blocked", "hrd-blocker", "hrd-muted", "hrd-mutedlater");

  @Autowired private PublicFeedQueryService feed;
  @Autowired private ForYouQueryService forYou;
  @Autowired private PublicSeriesQueryService seriesQuery;
  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private BlockRepository blockRepository;
  @Autowired private MuteRepository muteRepository;
  @Autowired private FollowRepository followRepository;
  @Autowired private SeriesRepository seriesRepository;
  @Autowired private SeriesItemRepository seriesItemRepository;
  @Autowired private SeriesSubscriptionRepository subscriptionRepository;
  @Autowired private UserTagPrefRepository tagPrefRepository;
  @Autowired private NoteRepository noteRepository;

  private long viewer;
  private final Map<String, Long> authors = new LinkedHashMap<>();

  @BeforeEach
  void viewerWhoBlockedMutedAndWasBlocked() {
    viewer = user("hrd-viewer");
    for (String handle : concat(HEARD, UNHEARD)) {
      long author = user(handle);
      authors.put(handle, author);
      publish(author, handle, null);
    }
    Instant now = Instant.now();
    blockRepository.save(new UserBlockEntity(viewer, authors.get("hrd-blocked")));
    blockRepository.save(new UserBlockEntity(authors.get("hrd-blocker"), viewer));
    muteRepository.save(new UserMuteEntity(viewer, authors.get("hrd-muted"), false, null));
    muteRepository.save(
        new UserMuteEntity(
            viewer, authors.get("hrd-mutedlater"), false, now.plus(1, ChronoUnit.HOURS)));
    muteRepository.save(
        new UserMuteEntity(
            viewer, authors.get("hrd-expired"), true, now.minus(1, ChronoUnit.HOURS)));
  }

  @Test
  void browsingRecentAndTrendingLeavesOutWhomTheViewerCannotHear() {
    for (String sort : List.of("recent", "trending")) {
      assertHeard(slugs(feed.feed(viewer, PublicFeedQuery.from(null, null, sort, null, 0, 50))));
      assertAllShown(slugs(feed.feed(null, PublicFeedQuery.from(null, null, sort, null, 0, 50))));
    }
  }

  @Test
  void aTopicInEitherSortListsAndCountsOnlyAuthorsTheViewerHears() {
    for (String sort : List.of("recent", "trending")) {
      PublicFeedView signedIn =
          feed.feed(viewer, PublicFeedQuery.from(null, TOPIC, sort, null, 0, 2));
      assertThat(slugs(signedIn)).containsExactlyInAnyOrderElementsOf(HEARD);
      assertThat(signedIn.hasNext()).isFalse();

      PublicFeedView anonymous =
          feed.feed(null, PublicFeedQuery.from(null, TOPIC, sort, null, 0, 2));
      assertThat(anonymous.hasNext()).isTrue();
      assertAllShown(slugs(feed.feed(null, PublicFeedQuery.from(null, TOPIC, sort, null, 0, 50))));
    }
  }

  @Test
  void searchByHandleInEverySortListsAndCountsOnlyAuthorsTheViewerHears() {
    for (String sort : List.of("relevance", "recent", "trending")) {
      PublicFeedView signedIn =
          feed.feed(viewer, PublicFeedQuery.from("hrd-", null, sort, null, 0, 2));
      assertThat(slugs(signedIn)).containsExactlyInAnyOrderElementsOf(HEARD);
      assertThat(signedIn.hasNext()).isFalse();

      assertAllShown(slugs(feed.feed(null, PublicFeedQuery.from("hrd-", null, sort, null, 0, 50))));
    }
  }

  @Test
  void topicSectionsLeaveOutWhomTheViewerCannotHear() {
    assertThat(sectionSlugs(feed.trendingByTag(viewer, 20, 20)))
        .containsExactlyInAnyOrderElementsOf(HEARD);
    assertAllShown(sectionSlugs(feed.trendingByTag(null, 20, 20)));
  }

  @Test
  void suggestedAuthorsLeaveOutWhomTheViewerCannotHear() {
    List<String> signedIn =
        feed.suggestedAuthors(viewer, 20).stream().map(s -> s.author().username()).toList();
    List<String> anonymous =
        feed.suggestedAuthors(null, 20).stream().map(s -> s.author().username()).toList();

    assertThat(signedIn).containsAll(HEARD).doesNotContainAnyElementsOf(UNHEARD);
    assertThat(anonymous).containsAll(concat(HEARD, UNHEARD));
  }

  @Test
  void seriesDiscoveryLeavesOutSeriesWhoseAuthorTheViewerCannotHear() {
    for (String handle : concat(HEARD, UNHEARD)) {
      seriesOfTwo(authors.get(handle), handle + "-series");
    }

    List<String> signedIn =
        seriesQuery.discoverSeries(viewer, 20).stream().map(PublicSeriesCard::slug).toList();
    List<String> anonymous =
        seriesQuery.discoverSeries(null, 20).stream().map(PublicSeriesCard::slug).toList();

    assertThat(signedIn).containsExactlyInAnyOrder("hrd-normal-series", "hrd-expired-series");
    assertThat(anonymous)
        .contains(
            "hrd-normal-series",
            "hrd-expired-series",
            "hrd-blocked-series",
            "hrd-blocker-series",
            "hrd-muted-series",
            "hrd-mutedlater-series");
  }

  @Test
  void theFollowingFeedLeavesOutFollowedAuthorsTheViewerCannotHear() {
    authors.values().forEach(author -> followRepository.save(new FollowEntity(viewer, author)));

    assertThat(slugs(feed.feedFollowing(viewer, 0, 50))).containsExactlyInAnyOrderElementsOf(HEARD);
  }

  @Test
  void aSubscribedSeriesFeedLeavesOutItsPostsAndNotesWhenTheViewerCannotHearTheAuthor() {
    long heardSeries = seriesWithNote(authors.get("hrd-normal"), "heard-series");
    long mutedSeries = seriesWithNote(authors.get("hrd-muted"), "muted-series");
    subscriptionRepository.insertIgnore(viewer, heardSeries);
    subscriptionRepository.insertIgnore(viewer, mutedSeries);

    PublicFeedView view = feed.feedFollowing(viewer, 0, 50);

    assertThat(slugs(view)).containsExactly("heard-series-post");
    assertThat(view.seriesNotes())
        .extracting(n -> n.author().username())
        .containsExactly("hrd-normal");
  }

  @Test
  void forYouLeavesOutWhomTheViewerCannotHearOnBothTheColdStartAndTheInterestPath() {
    assertHeard(slugs(forYou.feedForYou(viewer, 0, 50)));

    tagPrefRepository.save(new UserTagPrefEntity(viewer, TOPIC, TagPrefKind.FOLLOW));

    PublicFeedView interested = forYou.feedForYou(viewer, 0, 2);
    assertThat(slugs(interested)).containsExactlyInAnyOrderElementsOf(HEARD);
    assertThat(interested.hasNext()).isFalse();
  }

  private long user(String handle) {
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    return userRepository.save(u).getId();
  }

  private long publish(long author, String slug, Long seriesId) {
    PostEntity p = new PostEntity(author, slug, slug, "ko");
    p.updateTags(List.of(TOPIC));
    if (seriesId != null) p.assignToSeries(seriesId, 0);
    DiscoverableBodies.discoverable(p);
    p.publish();
    return postRepository.save(p).getId();
  }

  private void seriesOfTwo(long author, String slug) {
    long series = seriesRepository.save(new SeriesEntity(author, slug, slug)).getId();
    seriesItemRepository.replace(
        series,
        List.of(
            new SeriesItemEntity(
                series, SeriesItemType.POST, publish(author, slug + "-1", series), 0),
            new SeriesItemEntity(
                series, SeriesItemType.POST, publish(author, slug + "-2", series), 1)));
  }

  private long seriesWithNote(long author, String slug) {
    long series = seriesRepository.save(new SeriesEntity(author, slug, slug)).getId();
    long post = publish(author, slug + "-post", series);
    NoteEntity note = new NoteEntity(author, slug + " note", null, null);
    note.showTo(NoteVisibility.PUBLIC);
    long noteId = noteRepository.save(note).getId();
    seriesItemRepository.replace(
        series,
        List.of(
            new SeriesItemEntity(series, SeriesItemType.POST, post, 0),
            new SeriesItemEntity(series, SeriesItemType.NOTE, noteId, 1)));
    return series;
  }

  private static List<String> slugs(PublicFeedView view) {
    return view.items().stream().map(PublicFeedItem::slug).toList();
  }

  private static List<String> sectionSlugs(List<TrendingTagSection> sections) {
    return sections.stream()
        .filter(s -> s.tag().equals(TOPIC))
        .flatMap(s -> s.posts().stream())
        .map(PublicFeedItem::slug)
        .toList();
  }

  private static void assertHeard(List<String> slugs) {
    assertThat(slugs).containsAll(HEARD).doesNotContainAnyElementsOf(UNHEARD);
  }

  private static void assertAllShown(List<String> slugs) {
    assertThat(slugs).containsAll(concat(HEARD, UNHEARD));
  }

  private static List<String> concat(List<String> a, List<String> b) {
    List<String> all = new ArrayList<>(a);
    all.addAll(b);
    return all;
  }
}
