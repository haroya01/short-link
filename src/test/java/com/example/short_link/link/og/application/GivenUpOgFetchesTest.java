package com.example.short_link.link.og.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.repository.LinkRepository;
import org.junit.jupiter.api.Test;

class GivenUpOgFetchesTest {

  private final LinkRepository links = mock(LinkRepository.class);
  private final GivenUpOgFetches fetches = new GivenUpOgFetches(links);

  @Test
  void reopensTheLinkRowsAndCountsThem() {
    when(links.reopenGivenUpOgFetches()).thenReturn(4);

    assertThat(fetches.reopenGivenUp()).isEqualTo(4);
  }
}
