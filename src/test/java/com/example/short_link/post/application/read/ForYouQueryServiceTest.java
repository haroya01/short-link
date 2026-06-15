package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostReadEntity;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostReadRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ForYouQueryServiceTest {

  @Mock private PostRepository postRepository;
  @Mock private PostReadRepository postReadRepository;
  @Mock private PostLikeRepository postLikeRepository;
  @Mock private TagPrefQueryService tagPrefQueryService;
  @Mock private PostFeedItemAssembler feedItemAssembler;
  @Captor private ArgumentCaptor<List<String>> tagsCaptor;

  private ForYouQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new ForYouQueryService(
            postRepository,
            postReadRepository,
            postLikeRepository,
            tagPrefQueryService,
            feedItemAssembler);
  }

  private PostEntity post(long id, long authorId, List<String> tags) {
    PostEntity p = new PostEntity(authorId, "slug-" + id, "Title", "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    ReflectionTestUtils.setField(p, "tags", tags);
    return p;
  }

  private PostReadEntity read(long postId) {
    return new PostReadEntity(9L, postId, Instant.parse("2026-01-01T00:00:00Z"));
  }

  private PublicFeedItem item(long id, List<String> tags) {
    return new PublicFeedItem(
        id,
        new PublicAuthorView(2L, "bob", null, null),
        "slug-" + id,
        "Title",
        null,
        null,
        "ko",
        tags,
        Instant.parse("2026-01-01T00:00:00Z"),
        0,
        0);
  }

  @Test
  void personalizesFromFollowedAndReadTags() {
    when(tagPrefQueryService.get(9L)).thenReturn(new TagPrefsView(List.of("AI"), List.of()));
    when(postReadRepository.findByUserIdOrderByReadAtDesc(9L, 0, 200))
        .thenReturn(List.of(read(5L)));
    when(postLikeRepository.findAllByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
    when(postRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(post(5L, 2L, List.of("llm", "ai"))));
    PostEntity candidate = post(10L, 3L, List.of("ai"));
    when(postRepository.findForYouCandidates(
            eq(9L), anyCollection(), anyCollection(), eq(0), eq(20)))
        .thenReturn(List.of(candidate));
    when(postRepository.countForYouCandidates(eq(9L), anyCollection(), anyCollection()))
        .thenReturn(50L); // (0+1)*20 < 50 -> hasNext
    when(feedItemAssembler.assemble(anyList())).thenReturn(List.of(item(10L, List.of("ai"))));

    PublicFeedView view = service.feedForYou(9L, 0, 20);

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).id()).isEqualTo(10L);
    assertThat(view.items().get(0).followReason()).isNotNull();
    assertThat(view.items().get(0).followReason().tag()).isEqualTo("ai");
    assertThat(view.hasNext()).isTrue();
  }

  @Test
  void coldStartFallsBackToTrending() {
    when(tagPrefQueryService.get(9L)).thenReturn(new TagPrefsView(List.of(), List.of()));
    when(postReadRepository.findByUserIdOrderByReadAtDesc(9L, 0, 200)).thenReturn(List.of());
    when(postLikeRepository.findAllByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
    when(postRepository.findPublishedTrending(null, 0, 20))
        .thenReturn(List.of(post(7L, 2L, List.of("anything"))));
    when(postRepository.countPublished(null)).thenReturn(1L);
    when(feedItemAssembler.assemble(anyList())).thenReturn(List.of(item(7L, List.of("anything"))));

    PublicFeedView view = service.feedForYou(9L, 0, 20);

    assertThat(view.items()).extracting(PublicFeedItem::id).containsExactly(7L);
    assertThat(view.hasNext()).isFalse();
    verify(postRepository, never())
        .findForYouCandidates(any(), anyCollection(), anyCollection(), eq(0), eq(20));
  }

  @Test
  void coldStartReportsHasNextWhenMoreTrending() {
    when(tagPrefQueryService.get(9L)).thenReturn(new TagPrefsView(List.of(), List.of()));
    when(postReadRepository.findByUserIdOrderByReadAtDesc(9L, 0, 200)).thenReturn(List.of());
    when(postLikeRepository.findAllByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
    when(postRepository.findPublishedTrending(null, 0, 20))
        .thenReturn(List.of(post(7L, 2L, List.of("anything"))));
    when(postRepository.countPublished(null)).thenReturn(50L); // (0+1)*20 < 50 -> hasNext
    when(feedItemAssembler.assemble(anyList())).thenReturn(List.of(item(7L, List.of("anything"))));

    assertThat(service.feedForYou(9L, 0, 20).hasNext()).isTrue();
  }

  @Test
  void hiddenTagsAreExcludedFromInterest() {
    when(tagPrefQueryService.get(9L))
        .thenReturn(new TagPrefsView(List.of("AI", "crypto"), List.of("crypto")));
    when(postReadRepository.findByUserIdOrderByReadAtDesc(9L, 0, 200)).thenReturn(List.of());
    when(postLikeRepository.findAllByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
    when(postRepository.findForYouCandidates(
            eq(9L), tagsCaptor.capture(), anyCollection(), eq(0), eq(20)))
        .thenReturn(List.of());
    when(postRepository.countForYouCandidates(eq(9L), anyCollection(), anyCollection()))
        .thenReturn(0L);
    when(feedItemAssembler.assemble(anyList())).thenReturn(List.of());

    service.feedForYou(9L, 0, 20);

    assertThat(tagsCaptor.getValue()).contains("ai").doesNotContain("crypto");
  }
}
