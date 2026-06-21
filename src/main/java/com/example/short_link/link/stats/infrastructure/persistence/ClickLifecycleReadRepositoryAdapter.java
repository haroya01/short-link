package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.repository.ClickLifecycleReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DayClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HostFirstSeenRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReturnRateRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ClickLifecycleReadRepositoryAdapter implements ClickLifecycleReadRepository {

  private final JpaClickLifecycleReadRepository jpa;

  @Override
  public ReturnRateRow findReturnRate(Long linkId) {
    return jpa.findReturnRate(linkId);
  }

  @Override
  public List<DayClickRow> findLifecycleClicks(Long linkId, int maxDay) {
    return jpa.findLifecycleClicks(linkId, maxDay);
  }

  @Override
  public List<HostFirstSeenRow> findFirstSeenByReferrerHost(Long linkId) {
    return jpa.findFirstSeenByReferrerHost(linkId);
  }
}
