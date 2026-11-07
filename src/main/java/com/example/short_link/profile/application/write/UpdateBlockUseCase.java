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
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class UpdateBlockUseCase {

  private final ProfileBlockRepository profileBlockRepository;
  private final ProfileCacheEviction cacheEviction;
  private final BlockDestinations destinations;
  private final TransactionTemplate transaction;

  public ProfileBlockEntity execute(UpdateBlockCommand cmd) {
    ProfileBlockType type = ownedBlock(cmd).getType();
    if (type == ProfileBlockType.DIVIDER) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "divider has no content");
    }
    String validated = BlockContentValidator.validate(type, cmd.content());
    destinations.requireAllowed(type, validated);
    return transaction.execute(status -> update(cmd, validated));
  }

  private ProfileBlockEntity update(UpdateBlockCommand cmd, String validated) {
    ProfileBlockEntity block = ownedBlock(cmd);
    block.updateContent(validated);
    cacheEviction.evictByUserId(cmd.userId());
    return block;
  }

  private ProfileBlockEntity ownedBlock(UpdateBlockCommand cmd) {
    return profileBlockRepository
        .findById(cmd.blockId())
        .filter(b -> b.isOwnedBy(cmd.userId()))
        .orElseThrow(
            () ->
                new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, "block " + cmd.blockId()));
  }
}
