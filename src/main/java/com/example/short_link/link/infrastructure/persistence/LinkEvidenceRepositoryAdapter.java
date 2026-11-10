package com.example.short_link.link.infrastructure.persistence;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkEvidenceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LinkEvidenceRepositoryAdapter implements LinkEvidenceRepository {

  private final JpaLinkEvidenceQueries jpa;

  @Override
  public Optional<LinkFacts> findByShortCode(ShortCode shortCode) {
    return jpa.findFactsByShortCode(shortCode);
  }
}
