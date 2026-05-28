package com.example.short_link.link.profilebinding.infrastructure.persistence;

import com.example.short_link.link.profilebinding.domain.LinkProfileBindingEntity;
import com.example.short_link.link.profilebinding.domain.repository.LinkProfileBindingRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LinkProfileBindingRepositoryAdapter implements LinkProfileBindingRepository {

  private final JpaLinkProfileBindingRepository jpa;

  @Override
  public Optional<LinkProfileBindingEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public LinkProfileBindingEntity save(LinkProfileBindingEntity binding) {
    return jpa.save(binding);
  }
}
