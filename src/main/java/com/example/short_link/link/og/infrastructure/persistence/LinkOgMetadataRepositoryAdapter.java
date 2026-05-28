package com.example.short_link.link.og.infrastructure.persistence;

import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LinkOgMetadataRepositoryAdapter implements LinkOgMetadataRepository {

  private final JpaLinkOgMetadataRepository jpa;

  @Override
  public Optional<LinkOgMetadataEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public LinkOgMetadataEntity save(LinkOgMetadataEntity metadata) {
    return jpa.save(metadata);
  }
}
