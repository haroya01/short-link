package com.example.short_link.profile.application.write;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.link.exception.LinkNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ToggleLinkOnProfileUseCase {

  private final LinkRepository linkRepository;
  private final ProfileOrdering profileOrdering;

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public void execute(ToggleLinkOnProfileCommand cmd) {
    LinkEntity link =
        linkRepository
            .findByShortCode(cmd.shortCode())
            .orElseThrow(() -> new LinkNotFoundException(cmd.shortCode()));
    if (!link.isOwnedBy(cmd.userId())) {
      throw new LinkNotFoundException(cmd.shortCode());
    }
    if (cmd.show()) {
      link.setProfileOrder(profileOrdering.nextOrder(cmd.userId()));
    } else {
      link.setProfileOrder(null);
    }
  }
}
