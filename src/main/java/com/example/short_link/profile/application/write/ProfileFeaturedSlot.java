package com.example.short_link.profile.application.write;

import com.example.short_link.link.application.write.LinkProfilePlacement;
import com.example.short_link.link.application.write.ProfileLinkSlot;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 공개 프로필 맨 위의 대표 자리. 링크와 블록을 통틀어 한 사람당 하나라서, 새 대표를 세우면 나머지는 내려간다. */
@Component
@RequiredArgsConstructor
public class ProfileFeaturedSlot {

  private final LinkProfilePlacement linkPlacement;
  private final ProfileBlockRepository profileBlockRepository;

  public void featureLink(Long userId, ProfileLinkSlot link) {
    linkPlacement.unfeatureAllBut(userId, link.id());
    clearBlocks(userId, null);
    link.feature();
  }

  public void unfeatureLink(ProfileLinkSlot link) {
    link.unfeature();
  }

  public void featureBlock(Long userId, ProfileBlockEntity block) {
    linkPlacement.unfeatureAll(userId);
    clearBlocks(userId, block.getId());
    block.feature();
  }

  private void clearBlocks(Long userId, Long keepId) {
    for (ProfileBlockEntity other :
        profileBlockRepository.findAllByUserIdAndProfileHighlightedIsTrue(userId)) {
      if (!Objects.equals(other.getId(), keepId)) {
        other.unfeature();
      }
    }
  }
}
