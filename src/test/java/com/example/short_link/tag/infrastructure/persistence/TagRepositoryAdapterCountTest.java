package com.example.short_link.tag.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TagRepositoryAdapterCountTest {
  @Test
  void translatesGroupedRowsToTypedCounts() {
    JpaTagRepository jpa = mock(JpaTagRepository.class);
    when(jpa.countLinksByTagIds(List.of(1L, 2L)))
        .thenReturn(List.<Object[]>of(new Object[] {1L, 3L}));
    assertThat(new TagRepositoryAdapter(jpa).countLinksByTagIds(List.of(1L, 2L)))
        .isEqualTo(Map.of(1L, 3L));
  }

  @Test
  void emptySelectionDoesNotIssueAnInQuery() {
    JpaTagRepository jpa = mock(JpaTagRepository.class);
    assertThat(new TagRepositoryAdapter(jpa).countLinksByTagIds(List.of())).isEmpty();
    verifyNoInteractions(jpa);
  }
}
