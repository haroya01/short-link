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
public class ToggleLinkOnProfileUseCase {

  private final LinkRepository linkRepository;
  private final ProfileOrdering profileOrdering;
  private final ProfileCacheEviction cacheEviction;

  @Transactional
  public void execute(ToggleLinkOnProfileCommand cmd) {
    LinkEntity link =
        linkRepository
            .findByShortCode(cmd.shortCode())
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, cmd.shortCode()));
    if (!link.isOwnedBy(cmd.userId())) {
      throw new LinkException(LinkErrorCode.LINK_NOT_FOUND, cmd.shortCode());
    }
    if (cmd.show()) {
      link.setProfileOrder(profileOrdering.nextOrder(cmd.userId()));
    } else {
      link.setProfileOrder(null);
    }
    cacheEviction.evictByUserId(cmd.userId());
  }
}
