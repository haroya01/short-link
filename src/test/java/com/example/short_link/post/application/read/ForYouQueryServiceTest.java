package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostReadEntity;
import com.example.short_link.post.domain.feed.FeedCandidate;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostReadRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ForYouQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-10T00:00:00Z");
  private static final Instant WINDOW_START = NOW.minus(Duration.ofDays(7));

  @Mock private PostRepository postRepository;
  @Mock private PostReadRepository postReadRepository;
  @Mock private PostLikeRepository postLikeRepository;
  @Mock private PostViewEventRepository postViewEventRepository;
  @Mock private TagPrefQueryService tagPrefQueryService;
  @Mock private UserRepository userRepository;
  @Mock private PostFeedItemAssembler feedItemAssembler;

  private ForYouQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new ForYouQueryService(
            postRepository,
            postReadRepository,
            postLikeRepository,
            postViewEventRepository,
            tagPrefQueryService,
            userRepository,
            feedItemAssembler,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void viewer(String locale, List<String> followed, List<Long> reads) {
    when(tagPrefQueryService.get(9L)).thenReturn(new TagPrefsView(followed, List.of()));
    when(postReadRepository.findByUserIdOrderByReadAtDesc(9L, 0, 200))
        .thenReturn(reads.stream().map(id -> new PostReadEntity(9L, id, NOW)).toList());
    when(postLikeRepository.findAllByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
    UserEntity user = new UserEntity("v@x.com", "google", "g-v");
    user.updateLocale(locale);
    when(userRepository.findById(9L)).thenReturn(Optional.of(user));
  }

  private FeedCandidate candidate(
      long id, long authorId, String lang, long daysAgo, String... tags) {
    return new FeedCandidate(
        id, authorId, List.of(tags), lang, NOW.minus(Duration.ofDays(daysAgo)), null);
  }

  private PostEntity post(long id, long authorId, String lang, List<String> tags) {
    PostEntity p = new PostEntity(authorId, "slug-" + id, "Title", lang);
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    ReflectionTestUtils.setField(p, "tags", tags);
    return p;
  }

  private PublicFeedItem item(long id) {
    return new PublicFeedItem(
        id,
        new PublicAuthorView(2L, "bob", null, null),
        "slug-" + id,
        "Title",
        null,
        null,
        "ko",
        List.of(),
        NOW,
        0,
        0);
  }

  @Test
  void ranksWithTheCoreAndExplainsTheTopicItMatched() {
    viewer("ko", List.of("AI"), List.of(5L));
    when(postRepository.findAllByIdIn(List.of(5L)))
        .thenReturn(List.of(post(5L, 2L, "ko", List.of("llm", "ai"))));
    when(postRepository.findFeedCandidates(500))
        .thenReturn(
            List.of(
                candidate(12L, 9L, "ko", 0, "ai"),
                candidate(11L, 3L, "ko", 1, "AI"),
                candidate(10L, 4L, "ko", 2, "ai"),
                candidate(6L, 4L, "ko", 3, "go"),
                candidate(5L, 2L, "ko", 4, "llm", "ai")));
    when(postViewEventRepository.countHumanViewsSince(List.of(12L, 11L, 10L, 6L, 5L), WINDOW_START))
        .thenReturn(Map.of());
    when(postRepository.findAllByIdIn(List.of(10L)))
        .thenReturn(List.of(post(10L, 4L, "ko", List.of("ai"))));
    when(feedItemAssembler.assemble(anyList())).thenReturn(List.of(item(10L)));

    PublicFeedView view = service.feedForYou(9L, 1, 1);

    assertThat(view.items()).extracting(PublicFeedItem::id).containsExactly(10L);
    assertThat(view.items().get(0).followReason().tag()).isEqualTo("ai");
    assertThat(view.hasNext()).isTrue();
  }

  @Test
  void aReaderWithoutSignalsStillGetsTheirLanguageFirst() {
    viewer("ja", List.of(), List.of());
    when(postRepository.findFeedCandidates(500))
        .thenReturn(
            List.of(candidate(1L, 2L, "ko", 0, "java"), candidate(2L, 3L, "ja", 1, "java")));
    when(postViewEventRepository.countHumanViewsSince(anyCollection(), eq(WINDOW_START)))
        .thenReturn(Map.of());
    when(postRepository.findAllByIdIn(List.of(2L, 1L)))
        .thenReturn(
            List.of(post(1L, 2L, "ko", List.of("java")), post(2L, 3L, "ja", List.of("java"))));
    when(feedItemAssembler.assemble(anyList())).thenReturn(List.of(item(2L), item(1L)));

    PublicFeedView view = service.feedForYou(9L, 0, 20);

    assertThat(view.items()).extracting(PublicFeedItem::id).containsExactly(2L, 1L);
    assertThat(view.items()).allSatisfy(it -> assertThat(it.followReason()).isNull());
    assertThat(view.hasNext()).isFalse();
  }

  @Test
  void aPageBeyondTheRankingIsEmptyWithoutLoadingPosts() {
    viewer("ko", List.of("ai"), List.of());
    when(postRepository.findFeedCandidates(500))
        .thenReturn(List.of(candidate(10L, 3L, "ko", 1, "ai")));
    when(postViewEventRepository.countHumanViewsSince(anyCollection(), eq(WINDOW_START)))
        .thenReturn(Map.of());
    when(feedItemAssembler.assemble(List.of())).thenReturn(List.of());

    PublicFeedView view = service.feedForYou(9L, 3, 20);

    assertThat(view.items()).isEmpty();
    assertThat(view.hasNext()).isFalse();
    verify(postRepository, never()).findAllByIdIn(anyCollection());
  }
}
