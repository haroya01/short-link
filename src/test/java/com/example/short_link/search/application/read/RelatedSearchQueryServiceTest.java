package com.example.short_link.search.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.search.domain.RelatedTag;
import com.example.short_link.search.domain.repository.RelatedTagReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class RelatedSearchQueryServiceTest {

  private final RelatedTagReader reader = mock(RelatedTagReader.class);
  private final RelatedSearchQueryService service = new RelatedSearchQueryService(reader);

  @Test
  void asksForTagsWornWithEveryWordLeavingTheWordsOutAndCappingTheCount() {
    List<RelatedTag> found = List.of(new RelatedTag("Spring", 2, 1));
    when(reader.cooccurring("+(Java) +(성능)", List.of("java", "성능"), 200, 20)).thenReturn(found);

    assertThat(service.related(" #Java  성능 ", 99).tags()).isEqualTo(found);
    service.related("Java", 0);
    verify(reader).cooccurring("+(Java)", List.of("java"), 200, 1);
  }

  @Test
  void aQueryTheIndexCannotHoldAsksNothing() {
    assertThat(service.related("AI", 8).tags()).isEmpty();
    assertThat(service.related("", 8).tags()).isEmpty();
    assertThat(service.related("x".repeat(101), 8).tags()).isEmpty();
    verifyNoInteractions(reader);
  }
}
