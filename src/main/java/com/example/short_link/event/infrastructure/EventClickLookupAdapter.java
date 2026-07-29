package com.example.short_link.event.infrastructure;

import com.example.short_link.event.application.RegistrationClickLookup;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 최근 48시간 내 human 클릭만 후보 — 오래된 클릭 매칭은 채널 귀속의 신뢰를 깎는다. */
@Component
class EventClickLookupAdapter implements RegistrationClickLookup {

  private static final Duration LOOKBACK = Duration.ofHours(48);

  @PersistenceContext private EntityManager em;

  @Override
  public Optional<ClickSnapshot> findLatestHumanClick(List<LinkVisitor> candidates) {
    if (candidates.isEmpty()) return Optional.empty();
    StringBuilder jpql =
        new StringBuilder(
            "SELECT c FROM ClickEventEntity c WHERE c.bot = false AND c.clickedAt > :since AND (");
    for (int i = 0; i < candidates.size(); i++) {
      if (i > 0) jpql.append(" OR ");
      jpql.append("(c.linkId = :link")
          .append(i)
          .append(" AND c.visitorHash = :hash")
          .append(i)
          .append(")");
    }
    jpql.append(") ORDER BY c.clickedAt DESC");
    TypedQuery<ClickEventEntity> query =
        em.createQuery(jpql.toString(), ClickEventEntity.class)
            .setParameter("since", Instant.now().minus(LOOKBACK))
            .setMaxResults(1);
    for (int i = 0; i < candidates.size(); i++) {
      query.setParameter("link" + i, candidates.get(i).linkId());
      query.setParameter("hash" + i, candidates.get(i).visitorHash());
    }
    return query
        .getResultStream()
        .findFirst()
        .map(
            c ->
                new ClickSnapshot(
                    c.getLinkId(),
                    c.getSourceChannel(),
                    c.getClientApp(),
                    c.getReferrerHost(),
                    c.getUtmSource()));
  }
}
