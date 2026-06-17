package com.example.short_link.user.application.write;

import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Block / unblock another user (App Store 1.2 UGC requirement). Blocking yourself is rejected. The
 * block edge is idempotent — re-blocking someone already blocked is a no-op. Content hiding is done
 * client-side from the block list (see {@code BlockQueryService}).
 */
@Service
@RequiredArgsConstructor
public class BlockUseCase {

  private final UserRepository userRepository;
  private final BlockRepository blockRepository;

  @Transactional
  public void block(Long blockerId, String targetUsername) {
    UserEntity target = resolve(targetUsername);
    if (target.getId().equals(blockerId)) {
      throw new UserException(UserErrorCode.CANNOT_BLOCK_SELF);
    }
    if (!blockRepository.existsByBlockerIdAndBlockedId(blockerId, target.getId())) {
      blockRepository.save(new UserBlockEntity(blockerId, target.getId()));
    }
  }

  @Transactional
  public void unblock(Long blockerId, String targetUsername) {
    UserEntity target = resolve(targetUsername);
    blockRepository
        .findByBlockerIdAndBlockedId(blockerId, target.getId())
        .ifPresent(blockRepository::delete);
  }

  private UserEntity resolve(String username) {
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
  }
}
