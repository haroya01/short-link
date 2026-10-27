package com.example.short_link.profile.application.write;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 공개 프로필 맨 위의 대표 자리. 링크와 블록을 통틀어 한 사람당 하나라서, 새 대표를 세우면 나머지는 내려간다. */
@Component
@RequiredArgsConstructor
public class ProfileFeaturedSlot {

  private final LinkRepository linkRepository;
  private final ProfileBlockRepository profileBlockRepository;

  public void featureLink(Long userId, LinkEntity link) {
    clearLinks(userId, link.getId());
    clearBlocks(userId, null);
    link.featureOnProfile();
  }

  public void unfeatureLink(LinkEntity link) {
    link.unfeatureOnProfile();
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
}
