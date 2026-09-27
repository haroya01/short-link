package com.example.short_link.link.destination.application.write;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.expiration.domain.repository.LinkExpirationPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SetBlockedCountriesUseCase {

  private final LinkDestinationOwnership ownership;
  private final LinkExpirationPolicyRepository expirationPolicyRepository;
  private final LinkCacheEviction linkCacheEviction;

  @Transactional
  public LinkEntity execute(Long userId, ShortCode shortCode, String csv) {
    LinkEntity link = ownership.ownedLink(userId, shortCode);
    link.setBlockedCountries(csv);
    LinkExpirationPolicyEntity policy =
        expirationPolicyRepository
            .findById(link.getId())
            .orElseGet(() -> new LinkExpirationPolicyEntity(link.linkId()));
    policy.changeBlockedCountries(link.getBlockedCountries());
    expirationPolicyRepository.save(policy);
    linkCacheEviction.evictAfterCommit(shortCode);
    return link;
  }
}
