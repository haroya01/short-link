package com.example.short_link.link.application.write;

import com.example.short_link.link.access.domain.LinkAccessControlEntity;
import com.example.short_link.link.access.domain.repository.LinkAccessControlRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.expiration.domain.repository.LinkExpirationPolicyRepository;
import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkSidecars {

  private final LinkAccessControlRepository accessControls;
  private final LinkExpirationPolicyRepository expirationPolicies;
  private final LinkOgMetadataRepository ogMetadata;

  public void mirrorMaxViews(LinkEntity link) {
    LinkAccessControlEntity access = accessOf(link);
    access.mirrorMaxViews(link);
    accessControls.save(access);
  }

  public void mirrorPasswordAndMaxViews(LinkEntity link) {
    LinkAccessControlEntity access = accessOf(link);
    access.mirrorPasswordHash(link);
    access.mirrorMaxViews(link);
    accessControls.save(access);
  }

  public void mirrorBlockedCountries(LinkEntity link) {
    LinkExpirationPolicyEntity policy = expirationOf(link);
    policy.mirrorBlockedCountries(link);
    expirationPolicies.save(policy);
  }

  public void mirrorExpiredMessage(LinkEntity link) {
    LinkExpirationPolicyEntity policy = expirationOf(link);
    policy.mirrorExpiredMessage(link);
    expirationPolicies.save(policy);
  }

  public void mirrorCampaignExpiration(LinkEntity link) {
    LinkExpirationPolicyEntity policy = expirationOf(link);
    policy.mirrorExpiredMessage(link);
    policy.mirrorExpiredRedirectUrl(link);
    expirationPolicies.save(policy);
  }

  public void mirrorOgOverride(LinkEntity link) {
    LinkOgMetadataEntity og = ogOf(link);
    og.mirrorOverride(link);
    ogMetadata.save(og);
  }

  public void resetOgForNewUrl(LinkEntity link) {
    LinkOgMetadataEntity og = ogOf(link);
    og.resetForNewUrl();
    ogMetadata.save(og);
  }

  public void recordOgFetched(
      LinkEntity link, String title, String description, String image, Instant fetchedAt) {
    LinkOgMetadataEntity og = ogOf(link);
    og.applyFetched(title, description, image, fetchedAt);
    ogMetadata.save(og);
  }

  public void recordOgFetchFailed(LinkEntity link, Instant fetchedAt, boolean willRetry) {
    LinkOgMetadataEntity og = ogOf(link);
    og.markFetchFailed(fetchedAt, willRetry);
    ogMetadata.save(og);
  }

  private LinkAccessControlEntity accessOf(LinkEntity link) {
    return accessControls
        .findById(link.getId())
        .orElseGet(() -> new LinkAccessControlEntity(link.linkId()));
  }

  private LinkExpirationPolicyEntity expirationOf(LinkEntity link) {
    return expirationPolicies
        .findById(link.getId())
        .orElseGet(() -> new LinkExpirationPolicyEntity(link.linkId()));
  }

  private LinkOgMetadataEntity ogOf(LinkEntity link) {
    return ogMetadata
        .findById(link.getId())
        .orElseGet(() -> new LinkOgMetadataEntity(link.linkId()));
  }
}
