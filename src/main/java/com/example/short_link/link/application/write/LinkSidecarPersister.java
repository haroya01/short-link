package com.example.short_link.link.application.write;

import com.example.short_link.link.access.domain.LinkAccessControlEntity;
import com.example.short_link.link.access.domain.repository.LinkAccessControlRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.expiration.domain.repository.LinkExpirationPolicyRepository;
import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import com.example.short_link.link.profilebinding.domain.LinkProfileBindingEntity;
import com.example.short_link.link.profilebinding.domain.repository.LinkProfileBindingRepository;
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

  private final LinkOgMetadataRepository ogMetadataRepository;
  private final LinkAccessControlRepository accessControlRepository;
  private final LinkProfileBindingRepository profileBindingRepository;
  private final LinkExpirationPolicyRepository expirationPolicyRepository;

  void persistAll(LinkEntity saved) {
    ogMetadataRepository.save(new LinkOgMetadataEntity(saved.linkId()));
    accessControlRepository.save(new LinkAccessControlEntity(saved.linkId()));
    profileBindingRepository.save(new LinkProfileBindingEntity(saved.linkId()));
    expirationPolicyRepository.save(new LinkExpirationPolicyEntity(saved.linkId()));
  }
}
