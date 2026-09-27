package com.example.short_link.link.visit.infrastructure.persistence;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.visit.domain.LinkVisitOptionEntity;
import com.example.short_link.link.visit.domain.repository.LinkVisitOptionRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LinkVisitOptionRepositoryAdapter implements LinkVisitOptionRepository {

  private final JpaLinkVisitOptionRepository jpa;

  @Override
  public Optional<LinkVisitOptionEntity> findById(Long linkId) {
    return jpa.findById(linkId);
  }

  @Override
  public LinkVisitOptionEntity save(LinkVisitOptionEntity option) {
    return jpa.save(option);
  }

  @Override
  public List<ShortCode> findShortCodesUsingSplashCta(Long ctaId) {
    return jpa.findShortCodesUsingSplashCta(ctaId).stream().map(ShortCode::new).toList();
  }
}
