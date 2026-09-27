package com.example.short_link.post.infrastructure.persistence;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.short_link.post.domain.PostStatus;
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
  void unreadFeedAcceptsAnEmptyExclusionList() {
    JpaPostRepository jpa = mock(JpaPostRepository.class);
    PostRepositoryAdapter adapter = new PostRepositoryAdapter(jpa);
    adapter.findForYouCandidates(7L, List.of("java"), List.of(), 0, 20);
    adapter.countForYouCandidates(7L, List.of("java"), List.of());
    verify(jpa)
        .findForYouCandidates(
            7L, List.of("java"), List.of(-1L), PostStatus.PUBLISHED, PageRequest.of(0, 20));
    verify(jpa).countForYouCandidates(7L, List.of("java"), List.of(-1L), PostStatus.PUBLISHED);
  }
}
