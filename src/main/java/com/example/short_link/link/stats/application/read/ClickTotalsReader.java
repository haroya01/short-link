package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.stats.domain.projection.ClickProjections.LinkClickCount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ClickTotalsReader {

  long countHumanByLinkId(Long linkId);

  long countBotByLinkId(Long linkId);

  long countDirectByLinkId(Long linkId);

  long countProfileChannelByLinkId(Long linkId);

  UniqueVisitors uniqueVisitorsByLinkId(Long linkId);

  Instant findFirstClickAt(Long linkId);

  Instant findLastClickAt(Long linkId);

  long countPreviewByLinkId(Long linkId);

  long countSinceByLinkId(Long linkId, Instant since);

  long countDatacenterClicks(Long linkId);

  List<LinkClickCount> countsByLinkIds(List<Long> ids);

  List<LinkClickCount> humanCountsByLinkIds(List<Long> ids);

  List<LinkClickCount> countsByLinkIdsSince(List<Long> ids, Instant since);

  List<LinkClickCount> countsByLinkIdsBefore(List<Long> ids, Instant before);

  Instant findLastClickBeforeByLinkIds(List<Long> ids, Instant before);

  Map<Long, Map<String, Long>> humanClientAppCountsByLinkIds(Collection<Long> linkIds);

  record UniqueVisitors(long count, Instant rawSince) {}
}
