package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import java.time.Instant;
import java.util.List;

public interface ClickTotalsReadRepository {

  long countHumanByLinkId(Long linkId);

  long countBotByLinkId(Long linkId);

  long countDirectByLinkId(Long linkId);

  long countProfileChannelByLinkId(Long linkId);

  long countUniqueVisitorsByLinkId(Long linkId);

  Instant findFirstClickAt(Long linkId);

  Instant findLastClickAt(Long linkId);

  long countPreviewByLinkId(Long linkId);

  long countSinceByLinkId(Long linkId, Instant since);

  long countDatacenterClicks(Long linkId);

  List<LinkClickCount> countsByLinkIds(List<Long> ids);

  List<LinkClickCount> countsByLinkIdsSince(List<Long> ids, Instant since);

  List<LinkClickCount> countsByLinkIdsBefore(List<Long> ids, Instant before);

  Instant findLastClickBeforeByLinkIds(List<Long> ids, Instant before);
}
