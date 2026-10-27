package com.example.short_link.link.application;

import com.example.short_link.link.application.dto.LinkRef;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.expiration.domain.repository.LinkExpirationPolicyRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignLinks {

  private final LinkRepository links;
  private final LinkExpirationPolicyRepository expirationPolicies;
  private final LinkCacheEviction linkCacheEviction;

  public Optional<LinkRef> find(LinkId linkId) {
    return links.findById(linkId.value()).map(LinkRef::of);
  }

  public Optional<LinkRef> find(ShortCode shortCode) {
    return links.findByShortCode(shortCode).map(LinkRef::of);
  }

  public void expire(LinkId linkId, Instant at, String redirectUrl, String message) {
    LinkEntity link = require(linkId);
    link.applyCampaignExpiration(at, redirectUrl, message);
    LinkExpirationPolicyEntity policy =
        expirationPolicies
            .findById(link.getId())
            .orElseGet(() -> new LinkExpirationPolicyEntity(link.linkId()));
    policy.changeExpiredMessage(link.getExpiredMessage());
    policy.changeExpiredRedirectUrl(link.getExpiredRedirectUrl());
    expirationPolicies.save(policy);
  }

  public void delete(LinkId linkId) {
    LinkEntity link = require(linkId);
    links.delete(link);
    linkCacheEviction.evictAfterCommit(link);
  }

  public void evictAfterCommit(Collection<LinkId> linkIds) {
    linkCacheEviction.evictLinksAfterCommit(linkIds.stream().map(this::require).toList());
  }

  private LinkEntity require(LinkId linkId) {
    return links
        .findById(linkId.value())
        .orElseThrow(() -> new IllegalStateException("link missing: " + linkId.value()));
  }
}
