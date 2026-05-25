package com.example.short_link.profile.application.write;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkProfileBindingEntity;
import com.example.short_link.link.domain.repository.LinkProfileBindingRepository;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.profile.application.ProfileCacheEviction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mark exactly one of the user's featured links as the "hero". Setting it on a new link
 * automatically clears any previous highlight — there's only ever one big card.
 */
@Service
@RequiredArgsConstructor
public class SetLinkHighlightUseCase {

  private final LinkRepository linkRepository;
  private final LinkProfileBindingRepository profileBindingRepository;
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
      for (LinkEntity other :
          linkRepository.findAllByUserIdAndProfileHighlightedIsTrue(cmd.userId())) {
        if (!other.getId().equals(link.getId())) {
          other.setProfileHighlighted(false);
          mirrorHighlight(other.getId(), false);
        }
      }
      link.setProfileHighlighted(true);
      mirrorHighlight(link.getId(), true);
    } else {
      link.setProfileHighlighted(false);
      mirrorHighlight(link.getId(), false);
    }
    cacheEviction.evictByUserId(cmd.userId());
  }

  private void mirrorHighlight(Long linkId, boolean highlighted) {
    LinkProfileBindingEntity binding =
        profileBindingRepository
            .findById(linkId)
            .orElseGet(() -> new LinkProfileBindingEntity(linkId));
    binding.changeProfileHighlighted(highlighted);
    profileBindingRepository.save(binding);
  }
}
