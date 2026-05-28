package com.example.short_link.link.stats.infrastructure;

import com.example.short_link.link.stats.application.LinkClickEventReader;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaLinkClickEventReader implements LinkClickEventReader {

  private final ClickEventRepository repository;

  @Override
  public List<ClickEventEntity> latest(Long linkId, int limit) {
    return repository.findEventsByLinkIdLatest(linkId, limit);
  }

  @Override
  public List<ClickEventEntity> before(Long linkId, Long cursorId, int limit) {
    return repository.findEventsByLinkIdBefore(linkId, cursorId, limit);
  }
}
