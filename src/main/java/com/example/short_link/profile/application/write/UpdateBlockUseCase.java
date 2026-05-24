package com.example.short_link.profile.application.write;

import com.example.short_link.profile.application.BlockContentValidator;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockRepository;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.exception.InvalidUsernameException;
import com.example.short_link.profile.exception.ProfileNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateBlockUseCase {

  private final ProfileBlockRepository profileBlockRepository;

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public ProfileBlockEntity execute(UpdateBlockCommand cmd) {
    ProfileBlockEntity block =
        profileBlockRepository
            .findById(cmd.blockId())
            .filter(b -> b.isOwnedBy(cmd.userId()))
            .orElseThrow(() -> new ProfileNotFoundException("block " + cmd.blockId()));
    if (block.getType() == ProfileBlockType.DIVIDER) {
      throw new InvalidUsernameException("divider has no content");
    }
    block.updateContent(BlockContentValidator.validate(block.getType(), cmd.content()));
    return block;
  }
}
