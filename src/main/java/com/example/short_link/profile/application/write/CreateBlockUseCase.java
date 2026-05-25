package com.example.short_link.profile.application.write;

import com.example.short_link.profile.application.BlockContentValidator;
import com.example.short_link.profile.application.ProfileCacheEviction;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateBlockUseCase {

  private final ProfileBlockRepository profileBlockRepository;
  private final ProfileOrdering profileOrdering;
  private final ProfileCacheEviction cacheEviction;

  @Transactional
  public ProfileBlockEntity execute(CreateBlockCommand cmd) {
    String validated = BlockContentValidator.validate(cmd.type(), cmd.content());
    int next = profileOrdering.nextOrder(cmd.userId());
    ProfileBlockEntity block = new ProfileBlockEntity(cmd.userId(), cmd.type(), validated, next);
    ProfileBlockEntity saved = profileBlockRepository.save(block);
    cacheEviction.evictByUserId(cmd.userId());
    return saved;
  }
}
