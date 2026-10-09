package com.example.short_link.post.infrastructure.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.short_link.post.domain.DiscoveryQuality;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class PostRepositoryAdapterFeedParametersTest {
  @Test
  void followingListAndCountTranslateOnlyEmptyOperands() {
    JpaPostRepository jpa = mock(JpaPostRepository.class);
    PostRepositoryAdapter adapter = new PostRepositoryAdapter(jpa);
    adapter.findPublishedByAuthorsSeriesOrTags(9L, List.of(), List.of(3L), List.of(), 0, 20);
    adapter.countPublishedByAuthorsSeriesOrTags(9L, List.of(), List.of(3L), List.of());
    verify(jpa)
        .findPublishedByAuthorsSeriesOrTags(
            eq(List.of(-1L)),
            eq(List.of(3L)),
            eq(List.of("\u0000")),
            eq(9L),
            any(Instant.class),
            eq(PageRequest.of(0, 20)));
    verify(jpa)
        .countPublishedByAuthorsSeriesOrTags(
            eq(List.of(-1L)), eq(List.of(3L)), eq(List.of("\u0000")), eq(9L), any(Instant.class));
  }

  @Test
  void unreadFeedAcceptsAnEmptyExclusionList() {
    JpaPostRepository jpa = mock(JpaPostRepository.class);
    PostRepositoryAdapter adapter = new PostRepositoryAdapter(jpa);
    adapter.findForYouCandidates(7L, List.of("java"), List.of(), 0, 20);
    adapter.countForYouCandidates(7L, List.of("java"), List.of());
    verify(jpa)
        .findForYouCandidates(
            eq(7L),
            eq(List.of("java")),
            eq(List.of(-1L)),
            eq(DiscoveryQuality.MIN_BODY_TEXT_LENGTH),
            any(Instant.class),
            eq(PageRequest.of(0, 20)));
    verify(jpa)
        .countForYouCandidates(
            eq(7L),
            eq(List.of("java")),
            eq(List.of(-1L)),
            eq(DiscoveryQuality.MIN_BODY_TEXT_LENGTH),
            any(Instant.class));
  }

  @Test
  void anAnonymousReaderIsBoundAsNobodyAndASignedInOneAsThemselves() {
    JpaPostRepository jpa = mock(JpaPostRepository.class);
    PostRepositoryAdapter adapter = new PostRepositoryAdapter(jpa);
    adapter.findPublishedRecent(null, null, 0, 20);
    adapter.findPublishedRecent(7L, null, 0, 20);
    verify(jpa)
        .findPublishedRecent(
            isNull(),
            eq(DiscoveryQuality.MIN_BODY_TEXT_LENGTH),
            eq(-1L),
            any(Instant.class),
            eq(PageRequest.of(0, 20)));
    verify(jpa)
        .findPublishedRecent(
            isNull(),
            eq(DiscoveryQuality.MIN_BODY_TEXT_LENGTH),
            eq(7L),
            any(Instant.class),
            eq(PageRequest.of(0, 20)));
  }
}
