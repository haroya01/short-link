package com.example.short_link.profile.application.write;

import com.example.short_link.profile.application.BlockContentValidator;
import com.example.short_link.profile.application.ProfileCacheEviction;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateBlockUseCase {

  private final ProfileBlockRepository profileBlockRepository;
  private final ProfileCacheEviction cacheEviction;

  @Transactional
  public ProfileBlockEntity execute(UpdateBlockCommand cmd) {
    ProfileBlockEntity block =
        profileBlockRepository
            .findById(cmd.blockId())
            .filter(b -> b.isOwnedBy(cmd.userId()))
            .orElseThrow(
                () ->
                    new ProfileException(
                        ProfileErrorCode.PROFILE_NOT_FOUND, "block " + cmd.blockId()));
    if (block.getType() == ProfileBlockType.DIVIDER) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "divider has no content");
    }
    block.updateContent(BlockContentValidator.validate(block.getType(), cmd.content()));
    cacheEviction.evictByUserId(cmd.userId());
    return block;
  }
}
