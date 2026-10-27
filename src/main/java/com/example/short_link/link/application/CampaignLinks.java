package com.example.short_link.link.application;

import com.example.short_link.link.application.write.LinkSidecars;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.time.Instant;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignLinks {

  private final LinkRepository links;
  private final LinkSidecars sidecars;
  private final LinkCacheEviction linkCacheEviction;

  public void expire(LinkId linkId, Instant at, String redirectUrl, String message) {
    LinkEntity link = require(linkId);
    link.applyCampaignExpiration(at, redirectUrl, message);
    sidecars.mirrorCampaignExpiration(link);
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
