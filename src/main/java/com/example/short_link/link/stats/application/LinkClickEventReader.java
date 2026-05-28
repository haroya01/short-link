package com.example.short_link.link.stats.application;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import java.util.List;

public interface LinkClickEventReader {
  List<ClickEventEntity> latest(Long linkId, int limit);

  List<ClickEventEntity> before(Long linkId, Long cursorId, int limit);
}
