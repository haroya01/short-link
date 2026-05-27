package com.example.short_link.link.destination.infrastructure.persistence;

import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LinkDestinationRepositoryAdapter implements LinkDestinationRepository {

  private final JpaLinkDestinationRepository jpa;

  @Override
  public Optional<LinkDestinationEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public LinkDestinationEntity save(LinkDestinationEntity destination) {
    return jpa.save(destination);
  }

  @Override
  public void delete(LinkDestinationEntity destination) {
    jpa.delete(destination);
  }

  @Override
  public List<LinkDestinationEntity> findAllByLinkIdOrderByIdAsc(Long linkId) {
    return jpa.findAllByLinkIdOrderByIdAsc(linkId);
  }

  @Override
  public long countByLinkId(Long linkId) {
    return jpa.countByLinkId(linkId);
  }
}
