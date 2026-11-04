package com.example.short_link.link.og.application;

import com.example.short_link.common.link.LinkOgFetchReopener;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class GivenUpOgFetches implements LinkOgFetchReopener {

  private final LinkRepository links;
  private final LinkOgMetadataRepository metadata;

  @Override
  @Transactional
  public int reopenGivenUp() {
    metadata.reopenGivenUpFetches();
    return links.reopenGivenUpOgFetches();
  }
}
