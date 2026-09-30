package com.example.short_link.profile.application.write;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.profilebinding.domain.LinkProfileBindingEntity;
import com.example.short_link.link.profilebinding.domain.repository.LinkProfileBindingRepository;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 공개 프로필 맨 위의 대표 자리. 링크와 블록을 통틀어 한 사람당 하나라서, 새 대표를 세우면 나머지는 내려간다. 링크의 대표 표시는 link 와
 * link_profile_binding 두 곳에 있어 함께 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class ProfileFeaturedSlot {

  private final LinkRepository linkRepository;
  private final LinkProfileBindingRepository profileBindingRepository;
  private final ProfileBlockRepository profileBlockRepository;

  public void featureLink(Long userId, LinkEntity link) {
    clearLinks(userId, link.getId());
    clearBlocks(userId, null);
    link.featureOnProfile();
    mirrorLink(link.linkId(), true);
  }

  public void unfeatureLink(LinkEntity link) {
    link.unfeatureOnProfile();
    mirrorLink(link.linkId(), false);
  }

  public void featureBlock(Long userId, ProfileBlockEntity block) {
    clearLinks(userId, null);
    clearBlocks(userId, block.getId());
    block.feature();
  }

  private void clearLinks(Long userId, Long keepId) {
    for (LinkEntity other : linkRepository.findAllByUserIdAndProfileHighlightedIsTrue(userId)) {
      if (!Objects.equals(other.getId(), keepId)) {
        other.unfeatureOnProfile();
        mirrorLink(other.linkId(), false);
      }
    }
  }

  private void clearBlocks(Long userId, Long keepId) {
    for (ProfileBlockEntity other :
        profileBlockRepository.findAllByUserIdAndProfileHighlightedIsTrue(userId)) {
      if (!Objects.equals(other.getId(), keepId)) {
        other.unfeature();
      }
    }
  }

  private void mirrorLink(LinkId linkId, boolean highlighted) {
    LinkProfileBindingEntity binding =
        profileBindingRepository
            .findById(linkId.value())
            .orElseGet(() -> new LinkProfileBindingEntity(linkId));
    binding.changeProfileHighlighted(highlighted);
    profileBindingRepository.save(binding);
  }
}
