package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.DiscoveryQuality;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.feed.FeedCandidate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class PostRepositoryAdapterFeedParametersTest {
  @Test
  void followingListAndCountTranslateOnlyEmptyOperands() {
    JpaPostRepository jpa = mock(JpaPostRepository.class);
    PostRepositoryAdapter adapter = new PostRepositoryAdapter(jpa);
    adapter.findPublishedByAuthorsSeriesOrTags(List.of(), List.of(3L), List.of(), 0, 20);
    adapter.countPublishedByAuthorsSeriesOrTags(List.of(), List.of(3L), List.of());
    verify(jpa)
        .findPublishedByAuthorsSeriesOrTags(
            List.of(-1L),
            List.of(3L),
            List.of("\u0000"),
            PostStatus.PUBLISHED,
            PageRequest.of(0, 20));
    verify(jpa)
        .countPublishedByAuthorsSeriesOrTags(
            List.of(-1L), List.of(3L), List.of("\u0000"), PostStatus.PUBLISHED);
  }

  @Test
  void candidatePoolCarriesEachPostsTagsInOrderAndSkipsTheTagQueryWhenEmpty() {
    JpaPostRepository jpa = mock(JpaPostRepository.class);
    PostRepositoryAdapter adapter = new PostRepositoryAdapter(jpa);
    Instant at = Instant.parse("2026-07-01T00:00:00Z");
    when(jpa.findFeedCandidateRows(
            PostStatus.PUBLISHED, DiscoveryQuality.MIN_BODY_TEXT_LENGTH, PageRequest.of(0, 500)))
        .thenReturn(
            List.of(new Object[] {2L, 7L, "ja", at, 4L}, new Object[] {1L, 8L, "ko", at, null}));
    when(jpa.findTagRowsByPostIdIn(List.of(2L, 1L)))
        .thenReturn(List.of(new Object[] {2L, "Java"}, new Object[] {2L, "spring"}));

    assertThat(adapter.findFeedCandidates(500))
        .containsExactly(
            new FeedCandidate(2L, 7L, List.of("Java", "spring"), "ja", at, 4L),
            new FeedCandidate(1L, 8L, List.of(), "ko", at, null));

    JpaPostRepository emptyJpa = mock(JpaPostRepository.class);
    when(emptyJpa.findFeedCandidateRows(any(), anyInt(), any())).thenReturn(List.of());
    assertThat(new PostRepositoryAdapter(emptyJpa).findFeedCandidates(500)).isEmpty();
    verify(emptyJpa, never()).findTagRowsByPostIdIn(any());
  }
}
