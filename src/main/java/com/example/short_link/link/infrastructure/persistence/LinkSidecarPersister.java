package com.example.short_link.link.infrastructure.persistence;

import com.example.short_link.link.access.domain.LinkAccessControlEntity;
import com.example.short_link.link.application.write.LinkDefaultsWriter;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import com.example.short_link.link.profilebinding.domain.LinkProfileBindingEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Associated rows keep the main link row narrow for redirect lookups. Initialize them after the
 * link is saved.
 */
@Component
@RequiredArgsConstructor
public class LinkSidecarPersister implements LinkDefaultsWriter {

  private final EntityManager entityManager;

  @Override
  public void initialize(LinkId linkId) {
    entityManager.persist(new LinkOgMetadataEntity(linkId));
    entityManager.persist(new LinkAccessControlEntity(linkId));
    entityManager.persist(new LinkProfileBindingEntity(linkId));
    entityManager.persist(new LinkExpirationPolicyEntity(linkId));
  }
}
