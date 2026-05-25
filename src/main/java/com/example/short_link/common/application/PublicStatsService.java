package com.example.short_link.common.application;

import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickEventReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublicStatsService {

  private final LinkRepository linkRepository;
  private final ClickEventReadRepository clickEventRepository;

  @Cacheable("public-stats")
  public PublicStats totals() {
    return new PublicStats(linkRepository.count(), clickEventRepository.count());
  }
}
