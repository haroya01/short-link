package com.example.short_link.link.moderation.application;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.moderation.domain.LinkDisableReason;
import com.example.short_link.link.moderation.domain.LinkModerationEntity;
import com.example.short_link.link.moderation.domain.repository.LinkModerationRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkModerationService {

  private final LinkRepository links;
  private final LinkModerationRepository moderations;
  private final LinkCacheEviction linkCacheEviction;
  private final Clock clock;

  @Transactional
  public boolean disable(Long linkId, LinkDisableReason reason, Long adminUserId) {
    return switchOff(
        links
            .findById(linkId)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, linkId)),
        reason,
        adminUserId);
  }

  @Transactional
  public boolean disable(ShortCode shortCode, LinkDisableReason reason, Long adminUserId) {
    return switchOff(load(shortCode), reason, adminUserId);
  }

  @Transactional
  public boolean enable(ShortCode shortCode) {
    LinkEntity link = load(shortCode);
    return moderations
        .findByLinkId(link.getId())
        .map(
            moderation -> {
              moderations.delete(moderation);
              linkCacheEviction.evictAfterCommit(link);
              return true;
            })
        .orElse(false);
  }

  private boolean switchOff(LinkEntity link, LinkDisableReason reason, Long adminUserId) {
    if (moderations.findByLinkId(link.getId()).isPresent()) {
      return false;
    }
    moderations.insert(
        new LinkModerationEntity(link.getId(), reason, adminUserId, clock.instant()));
    linkCacheEviction.evictAfterCommit(link);
    return true;
  }

  private LinkEntity load(ShortCode shortCode) {
    return links
        .findByShortCode(shortCode)
        .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode.value()));
  }
}
