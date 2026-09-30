package com.example.short_link.profile.application.write;

import com.example.short_link.profile.application.ProfileCacheEviction;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SetBlockHighlightUseCase {

  private final ProfileBlockRepository profileBlockRepository;
  private final ProfileFeaturedSlot featuredSlot;
  private final ProfileCacheEviction cacheEviction;

  @Transactional
  public void execute(SetBlockHighlightCommand cmd) {
    ProfileBlockEntity block =
        profileBlockRepository
            .findById(cmd.blockId())
            .filter(b -> b.isOwnedBy(cmd.userId()))
            .orElseThrow(
                () ->
                    new ProfileException(
                        ProfileErrorCode.PROFILE_NOT_FOUND, "block " + cmd.blockId()));
    if (!block.isHighlightable()) {
      throw new ProfileException(ProfileErrorCode.BLOCK_NOT_HIGHLIGHTABLE, block.getType());
    }
    if (cmd.highlighted()) {
      featuredSlot.featureBlock(cmd.userId(), block);
    } else {
      block.unfeature();
    }
    cacheEviction.evictByUserId(cmd.userId());
  }
}
