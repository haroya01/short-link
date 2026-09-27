package com.example.short_link.link.health.infrastructure.persistence;

import com.example.short_link.link.health.domain.LinkDestinationHealthEntity;
import com.example.short_link.link.health.domain.repository.LinkDestinationHealthRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LinkDestinationHealthRepositoryAdapter implements LinkDestinationHealthRepository {

  private final JpaLinkDestinationHealthRepository jpa;

  @Override
  public Optional<LinkDestinationHealthEntity> findById(Long linkId) {
    return jpa.findById(linkId);
  }

  @Override
  public LinkDestinationHealthEntity save(LinkDestinationHealthEntity health) {
    return jpa.save(health);
  }

  @Override
  public List<DueDestination> findDue(Instant now, Instant checkedBefore, int limit) {
    return jpa.findDue(now, checkedBefore, PageRequest.of(0, limit)).stream()
        .map(
            row ->
                new DueDestination(
                    row.getLinkId(),
                    row.getShortCode().value(),
                    row.getUserId(),
                    row.getOriginalUrl(),
                    row.getNote()))
        .toList();
  }
}
