package com.example.short_link.profile.application.write;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.profile.application.ProfileCacheEviction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SetLinkHighlightUseCase {

  private final LinkRepository linkRepository;
  private final ProfileFeaturedSlot featuredSlot;
  private final ProfileCacheEviction cacheEviction;

  @Transactional
  public void execute(SetLinkHighlightCommand cmd) {
    LinkEntity link =
        linkRepository
            .findByShortCode(cmd.shortCode())
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, cmd.shortCode()));
    if (!link.isOwnedBy(cmd.userId()))
      throw new LinkException(LinkErrorCode.LINK_NOT_FOUND, cmd.shortCode());
    if (cmd.highlighted()) {
      featuredSlot.featureLink(cmd.userId(), link);
    } else {
      featuredSlot.unfeatureLink(link);
    }
    cacheEviction.evictByUserId(cmd.userId());
  }
}
