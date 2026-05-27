package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublicStatsService {

  private final LinkRepository linkRepository;
  private final ClickEventRepository clickEventRepository;

  @Cacheable("public-stats")
  public PublicStats totals() {
    return new PublicStats(linkRepository.count(), clickEventRepository.count());
  }
}
