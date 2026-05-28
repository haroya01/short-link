package com.example.short_link.link.access.infrastructure.persistence;

import com.example.short_link.link.access.domain.LinkAccessControlEntity;
import com.example.short_link.link.access.domain.repository.LinkAccessControlRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LinkAccessControlRepositoryAdapter implements LinkAccessControlRepository {

  private final JpaLinkAccessControlRepository jpa;

  @Override
  public Optional<LinkAccessControlEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public LinkAccessControlEntity save(LinkAccessControlEntity access) {
    return jpa.save(access);
  }
}
