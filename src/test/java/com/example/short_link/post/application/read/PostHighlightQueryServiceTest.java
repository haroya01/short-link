package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
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
class PostHighlightQueryServiceTest {

  @Mock private PostHighlightRepository highlightRepository;
  @Mock private PostHighlightReplyRepository replyRepository;
  @Mock private PostRepository postRepository;
  @Mock private UserRepository userRepository;
  @Mock private FollowRepository followRepository;

  private PostHighlightQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new PostHighlightQueryService(
            highlightRepository, replyRepository, postRepository, userRepository, followRepository);
  }

  private PostEntity publishedPost(long id, long authorId) {
    PostEntity p = new PostEntity(authorId, "slug-" + id, "Title " + id, "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private PostHighlightEntity highlight(long id, long postId, long userId) {
    PostHighlightEntity h =
        new PostHighlightEntity(postId, userId, 0, 0, 0, 3, "quote-" + id, null);
    ReflectionTestUtils.setField(h, "id", id);
    return h;
  }

  private PostHighlightEntity highlightWithNote(long id, long postId, long userId, String note) {
    PostHighlightEntity h =
        new PostHighlightEntity(postId, userId, 0, 0, 0, 3, "quote-" + id, note);
    ReflectionTestUtils.setField(h, "id", id);
    return h;
  }

  private PostHighlightEntity multiBlockHighlight(
      long id, long postId, long userId, int blockOrder, int endBlockOrder) {
    PostHighlightEntity h =
        new PostHighlightEntity(
            postId, userId, blockOrder, endBlockOrder, 0, 3, "quote-" + id, null);
    ReflectionTestUtils.setField(h, "id", id);
    return h;
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void listForPostAttributesHighlightsAndNullsMissingAuthor() {
    when(postRepository.findById(5L)).thenReturn(Optional.of(publishedPost(5L, 1L)));
    when(highlightRepository.findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(5L))
        .thenReturn(List.of(highlightWithNote(10L, 5L, 1L, "여백의 메모"), highlight(11L, 5L, 999L)));
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(1L, "alice")));

    List<HighlightView> views = service.listForPost(5L);

    assertThat(views).hasSize(2);
    assertThat(views.get(0).author().username()).isEqualTo("alice");
    assertThat(views.get(0).note()).isEqualTo("여백의 메모");
    assertThat(views.get(1).author()).isNull(); // user 999 not found
    assertThat(views.get(1).note()).isNull();
  }

  @Test
  void listForPostExposesEndBlockOrderForSingleAndMultiBlockSpans() {
    when(postRepository.findById(5L)).thenReturn(Optional.of(publishedPost(5L, 1L)));
    when(highlightRepository.findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(5L))
        .thenReturn(
            List.of(
                highlight(10L, 5L, 1L), // 단일 블록: endBlockOrder == blockOrder == 0
                multiBlockHighlight(11L, 5L, 1L, 2, 5))); // 여러 블록: 2 → 5
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(1L, "alice")));

    List<HighlightView> views = service.listForPost(5L);

    assertThat(views.get(0).blockOrder()).isEqualTo(0);
    assertThat(views.get(0).endBlockOrder()).isEqualTo(0);
    assertThat(views.get(1).blockOrder()).isEqualTo(2);
    assertThat(views.get(1).endBlockOrder()).isEqualTo(5);
  }

  @Test
  void listForPostHydratesReplyCountsInOneBatch() {
    when(postRepository.findById(5L)).thenReturn(Optional.of(publishedPost(5L, 1L)));
    when(highlightRepository.findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(5L))
        .thenReturn(List.of(highlight(10L, 5L, 1L), highlight(11L, 5L, 1L)));
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(1L, "alice")));
    when(replyRepository.countByHighlightIds(anyCollection())).thenReturn(Map.of(10L, 3L));

    List<HighlightView> views = service.listForPost(5L);

    assertThat(views).hasSize(2);
    assertThat(views.get(0).replyCount()).isEqualTo(3L);
    assertThat(views.get(1).replyCount()).isZero(); // no replies → 0, not null
  }

  @Test
  void listForPostEmptyWhenPostNotPublished() {
    PostEntity draft = new PostEntity(1L, "draft", "Draft", "ko");
    ReflectionTestUtils.setField(draft, "id", 5L);
    when(postRepository.findById(5L)).thenReturn(Optional.of(draft));

    assertThat(service.listForPost(5L)).isEmpty();
  }

  @Test
  void listMineCarriesPostRefAndNullsWhenPostOrAuthorMissing() {
    // hA: post present + author present | hB: post missing | hC: post present + author missing
    when(highlightRepository.findAllByUserIdOrderByCreatedAtDesc(1L))
        .thenReturn(
            List.of(
                highlightWithNote(10L, 5L, 1L, "내 메모"),
                highlight(11L, 999L, 1L),
                highlight(12L, 6L, 1L)));
    when(postRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(publishedPost(5L, 2L), publishedPost(6L, 3L)));
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(2L, "bob")));

    List<MyHighlightView> views = service.listMine(1L);

    assertThat(views).hasSize(3);
    MyHighlightView present = views.get(0);
    assertThat(present.postUsername()).isEqualTo("bob");
    assertThat(present.postSlug()).isEqualTo("slug-5");
    assertThat(present.postTitle()).isEqualTo("Title 5");
    assertThat(present.note()).isEqualTo("내 메모");

    MyHighlightView postMissing = views.get(1);
    assertThat(postMissing.postUsername()).isNull();
    assertThat(postMissing.postSlug()).isNull();
    assertThat(postMissing.postTitle()).isNull();

    MyHighlightView authorMissing = views.get(2);
    assertThat(authorMissing.postUsername()).isNull(); // author 3 not found
    assertThat(authorMissing.postSlug()).isEqualTo("slug-6");
  }

  // 팔로우 0 = 콜드스타트 — 빈 화면 대신 전역 공개 하이라이트로 폴백한다(source=global).
  @Test
  void feedFallsBackToGlobalWhenViewerFollowsNoOne() {
    when(followRepository.findFollowingIds(1L)).thenReturn(List.of());
    when(highlightRepository.findRecentOnPublishedPosts(0, 20))
        .thenReturn(List.of(highlight(10L, 5L, 9L)));
    when(postRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(publishedPost(5L, 2L)));
    when(userRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(user(9L, "stranger"), user(2L, "bob")));

    HighlightFeedView feed = service.feed(1L, 0, 20, false);

    assertThat(feed.source()).isEqualTo("global");
    assertThat(feed.items()).hasSize(1);
    assertThat(feed.items().get(0).curator().username()).isEqualTo("stranger");
    verify(highlightRepository, never())
        .findByUserIdsOrderByCreatedAtDesc(anyCollection(), anyInt(), anyInt());
  }

  // 팔로우는 있는데 그들이 칠한 구절이 0 — 첫 페이지만 전역으로 폴백한다.
  @Test
  void feedFallsBackToGlobalOnFirstPageWhenFollowedCuratorsAreQuiet() {
    when(followRepository.findFollowingIds(1L)).thenReturn(List.of(2L));
    when(highlightRepository.findByUserIdsOrderByCreatedAtDesc(anyCollection(), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(highlightRepository.findRecentOnPublishedPosts(0, 20))
        .thenReturn(List.of(highlight(10L, 5L, 9L)));
    when(postRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(publishedPost(5L, 2L)));
    when(userRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(user(9L, "stranger"), user(2L, "bob")));

    HighlightFeedView feed = service.feed(1L, 0, 20, false);

    assertThat(feed.source()).isEqualTo("global");
    assertThat(feed.items()).hasSize(1);
  }

  // 1페이지 이후의 빈 결과는 개인화 피드의 정상 종료 — 전역을 섞으면 페이지가 오염되므로 폴백하지 않는다.
  @Test
  void feedDoesNotFallBackBeyondFirstPage() {
    when(followRepository.findFollowingIds(1L)).thenReturn(List.of(2L));
    when(highlightRepository.findByUserIdsOrderByCreatedAtDesc(anyCollection(), anyInt(), anyInt()))
        .thenReturn(List.of());

    HighlightFeedView feed = service.feed(1L, 1, 20, false);

    assertThat(feed.source()).isEqualTo("following");
    assertThat(feed.items()).isEmpty();
    assertThat(feed.hasNext()).isFalse();
    verify(highlightRepository, never()).findRecentOnPublishedPosts(anyInt(), anyInt());
  }

  // scope=global 고정 — 팔로우 그래프를 아예 묻지 않고 전역 페이지네이션을 이어간다.
  @Test
  void feedForceGlobalPinsGlobalFeedRegardlessOfFollowGraph() {
    when(highlightRepository.findRecentOnPublishedPosts(2, 10)).thenReturn(List.of());

    HighlightFeedView feed = service.feed(1L, 2, 10, true);

    assertThat(feed.source()).isEqualTo("global");
    assertThat(feed.items()).isEmpty();
    verifyNoInteractions(followRepository);
  }

  @Test
  void feedAssemblesFollowedCuratorHighlightsWithPostRefAndSkipsDeletedPosts() {
    // 팔로우한 큐레이터(alice, id 1)가 그은 두 구절 — 하나는 살아있는 글(작가 bob), 하나는 소실된 글.
    when(followRepository.findFollowingIds(1L)).thenReturn(List.of(1L));
    when(highlightRepository.findByUserIdsOrderByCreatedAtDesc(anyCollection(), anyInt(), anyInt()))
        .thenReturn(
            List.of(
                highlightWithNote(10L, 5L, 1L, "여백의 메모"),
                highlight(11L, 999L, 1L))); // 글 999 소실 → 스킵
    when(postRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(publishedPost(5L, 2L)));
    when(userRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(user(1L, "alice"), user(2L, "bob")));
    when(replyRepository.countByHighlightIds(anyCollection())).thenReturn(Map.of(10L, 2L));

    HighlightFeedView feed = service.feed(1L, 0, 20, false);

    assertThat(feed.items()).hasSize(1); // 소실된 글의 하이라이트는 빠진다
    assertThat(feed.source()).isEqualTo("following"); // 개인화가 채워졌으니 폴백 없음
    HighlightFeedItem item = feed.items().get(0);
    assertThat(item.curator().username()).isEqualTo("alice"); // 누가 칠했나
    assertThat(item.postAuthorUsername()).isEqualTo("bob"); // 글 작가
    assertThat(item.postSlug()).isEqualTo("slug-5");
    assertThat(item.postTitle()).isEqualTo("Title 5");
    assertThat(item.note()).isEqualTo("여백의 메모");
    assertThat(item.replyCount()).isEqualTo(2L);
  }
}
