package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ClickTotalsReadRepositoryAdapter implements ClickTotalsReadRepository {

  private final JpaClickTotalsReadRepository jpa;

  @Override
  public long countHumanByLinkId(Long linkId) {
    return jpa.countHumanByLinkId(linkId);
  }

  @Override
  public long countBotByLinkId(Long linkId) {
    return jpa.countBotByLinkId(linkId);
  }

  @Override
  public long countDirectByLinkId(Long linkId) {
    return jpa.countDirectByLinkId(linkId);
  }

  @Override
  public long countProfileChannelByLinkId(Long linkId) {
    return jpa.countProfileChannelByLinkId(linkId);
  }

  @Override
  public long countUniqueVisitorsByLinkId(Long linkId) {
    return jpa.countUniqueVisitorsByLinkId(linkId);
  }

  @Override
  public Instant findFirstClickAt(Long linkId) {
    return jpa.findFirstClickAt(linkId);
  }

  @Override
  public Instant findLastClickAt(Long linkId) {
    return jpa.findLastClickAt(linkId);
  }

  @Override
  public long countPreviewByLinkId(Long linkId) {
    return jpa.countPreviewByLinkId(linkId);
  }

  @Override
  public long countSinceByLinkId(Long linkId, Instant since) {
    return jpa.countSinceByLinkId(linkId, since);
  }

  @Override
  public long countDatacenterClicks(Long linkId) {
    return jpa.countDatacenterClicks(linkId);
  }

  @Override
  public List<LinkClickCount> countsByLinkIds(List<Long> ids) {
    return jpa.countsByLinkIds(ids);
  }

  @Override
  public List<LinkClickCount> countsByLinkIdsSince(List<Long> ids, Instant since) {
    return jpa.countsByLinkIdsSince(ids, since);
  }

  @Override
  public List<LinkClickCount> countsByLinkIdsBefore(List<Long> ids, Instant before) {
    return jpa.countsByLinkIdsBefore(ids, before);
  }

  @Override
  public Instant findLastClickBeforeByLinkIds(List<Long> ids, Instant before) {
    return jpa.findLastClickBeforeByLinkIds(ids, before);
  }
}
