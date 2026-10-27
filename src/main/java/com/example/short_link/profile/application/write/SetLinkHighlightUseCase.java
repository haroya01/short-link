package com.example.short_link.profile.application.write;

import com.example.short_link.link.application.write.LinkProfilePlacement;
import com.example.short_link.link.application.write.ProfileLinkSlot;
import com.example.short_link.profile.application.ProfileCacheEviction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SetLinkHighlightUseCase {

  private final LinkProfilePlacement linkPlacement;
  private final ProfileFeaturedSlot featuredSlot;
  private final ProfileCacheEviction cacheEviction;

  @Transactional
  public void execute(SetLinkHighlightCommand cmd) {
    ProfileLinkSlot link = linkPlacement.requireOwn(cmd.userId(), cmd.shortCode());
    if (cmd.highlighted()) {
      featuredSlot.featureLink(cmd.userId(), link);
    } else {
      featuredSlot.unfeatureLink(link);
    }
    cacheEviction.evictByUserId(cmd.userId());
  }
}
