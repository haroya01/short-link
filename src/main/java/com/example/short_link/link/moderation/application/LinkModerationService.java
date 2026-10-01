package com.example.short_link.link.moderation.application;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.domain.LinkEntity;
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
    LinkEntity link = load(linkId);
    if (moderations.findByLinkId(linkId).isPresent()) {
      return false;
    }
    moderations.insert(new LinkModerationEntity(linkId, reason, adminUserId, clock.instant()));
    linkCacheEviction.evictAfterCommit(link.getShortCode());
    return true;
  }

  @Transactional
  public boolean enable(Long linkId) {
    LinkEntity link = load(linkId);
    return moderations
        .findByLinkId(linkId)
        .map(
            moderation -> {
              moderations.delete(moderation);
              linkCacheEviction.evictAfterCommit(link.getShortCode());
              return true;
            })
        .orElse(false);
  }

  private LinkEntity load(Long linkId) {
    return links
        .findById(linkId)
        .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, linkId));
  }
}
