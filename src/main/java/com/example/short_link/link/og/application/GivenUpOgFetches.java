package com.example.short_link.link.og.application;

import com.example.short_link.common.link.LinkOgFetchReopener;
import com.example.short_link.link.domain.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class GivenUpOgFetches implements LinkOgFetchReopener {

  private final LinkRepository links;

  @Override
  public int reopenGivenUp() {
    return links.reopenGivenUpOgFetches();
  }
}
