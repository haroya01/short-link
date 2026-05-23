package com.example.short_link.profile.application.write;

import com.example.short_link.profile.application.ProfileNotFoundException;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteBlockUseCase {

  private final ProfileBlockRepository profileBlockRepository;

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public void execute(DeleteBlockCommand cmd) {
    ProfileBlockEntity block =
        profileBlockRepository
            .findById(cmd.blockId())
            .filter(b -> b.isOwnedBy(cmd.userId()))
            .orElseThrow(() -> new ProfileNotFoundException("block " + cmd.blockId()));
    profileBlockRepository.delete(block);
  }
}
