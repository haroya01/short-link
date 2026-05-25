package com.example.short_link.link.destination.application.write;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.domain.repository.LinkExpirationPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SetBlockedCountriesUseCase {

  private final LinkDestinationOwnership ownership;
  private final LinkExpirationPolicyRepository expirationPolicyRepository;

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public LinkEntity execute(Long userId, String shortCode, String csv) {
    LinkEntity link = ownership.ownedLink(userId, shortCode);
    link.setBlockedCountries(csv);
    LinkExpirationPolicyEntity policy =
        expirationPolicyRepository
            .findById(link.getId())
            .orElseGet(() -> new LinkExpirationPolicyEntity(link.getId()));
    policy.changeBlockedCountries(link.getBlockedCountries());
    expirationPolicyRepository.save(policy);
    return link;
  }
}
