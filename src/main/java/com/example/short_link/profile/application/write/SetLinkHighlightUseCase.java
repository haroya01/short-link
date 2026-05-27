package com.example.short_link.profile.application.write;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.profilebinding.domain.LinkProfileBindingEntity;
import com.example.short_link.link.profilebinding.domain.repository.LinkProfileBindingRepository;
import com.example.short_link.profile.application.ProfileCacheEviction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
          mirrorHighlight(other.linkId(), false);
        }
      }
      link.setProfileHighlighted(true);
      mirrorHighlight(link.linkId(), true);
    } else {
      link.setProfileHighlighted(false);
      mirrorHighlight(link.linkId(), false);
    }
    cacheEviction.evictByUserId(cmd.userId());
  }

  private void mirrorHighlight(LinkId linkId, boolean highlighted) {
    LinkProfileBindingEntity binding =
        profileBindingRepository
            .findById(linkId.value())
            .orElseGet(() -> new LinkProfileBindingEntity(linkId));
    binding.changeProfileHighlighted(highlighted);
    profileBindingRepository.save(binding);
  }
}
