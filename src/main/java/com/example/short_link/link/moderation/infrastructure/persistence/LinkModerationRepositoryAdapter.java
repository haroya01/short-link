package com.example.short_link.link.moderation.infrastructure.persistence;

import com.example.short_link.link.moderation.domain.LinkModerationEntity;
import com.example.short_link.link.moderation.domain.repository.LinkModerationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LinkModerationRepositoryAdapter implements LinkModerationRepository {

  private final JpaLinkModerationRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public Optional<LinkModerationEntity> findByLinkId(Long linkId) {
    return jpa.findById(linkId);
  }

  // The id is the link's, so persist() inserts directly where save() would merge and select first.
  @Override
  public void insert(LinkModerationEntity moderation) {
    em.persist(moderation);
  }

  @Override
  public void delete(LinkModerationEntity moderation) {
    jpa.delete(moderation);
  }
}
