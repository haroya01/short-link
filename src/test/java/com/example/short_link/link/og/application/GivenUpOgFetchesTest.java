package com.example.short_link.link.og.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import org.junit.jupiter.api.Test;

class GivenUpOgFetchesTest {

  private final LinkRepository links = mock(LinkRepository.class);
  private final LinkOgMetadataRepository metadata = mock(LinkOgMetadataRepository.class);
  private final GivenUpOgFetches fetches = new GivenUpOgFetches(links, metadata);

  @Test
  void reopensTheLinkRowAndItsMetadataMirrorAndCountsTheLinks() {
    when(metadata.reopenGivenUpFetches()).thenReturn(4);
    when(links.reopenGivenUpOgFetches()).thenReturn(4);

    assertThat(fetches.reopenGivenUp()).isEqualTo(4);

    var order = inOrder(metadata, links);
    order.verify(metadata).reopenGivenUpFetches();
    order.verify(links).reopenGivenUpOgFetches();
  }
}
