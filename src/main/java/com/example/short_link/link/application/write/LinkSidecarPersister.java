package com.example.short_link.link.application.write;

import com.example.short_link.link.access.domain.LinkAccessControlEntity;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import com.example.short_link.link.profilebinding.domain.LinkProfileBindingEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Creates the 4 side-tables that every link gets (og metadata, access control, profile binding,
 * expiration policy). Splitting these out of LinkEntity keeps the main row narrow on the hot
 * redirect path; this helper hides the fan-out so the create UseCase stays focused on the link
 * itself.
 */
@Component
@RequiredArgsConstructor
class LinkSidecarPersister {

  private final EntityManager entityManager;

  void persistAll(LinkEntity saved) {
    entityManager.persist(new LinkOgMetadataEntity(saved.linkId()));
    entityManager.persist(new LinkAccessControlEntity(saved.linkId()));
    entityManager.persist(new LinkProfileBindingEntity(saved.linkId()));
    entityManager.persist(new LinkExpirationPolicyEntity(saved.linkId()));
  }
}
