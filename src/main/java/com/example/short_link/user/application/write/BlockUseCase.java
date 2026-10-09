package com.example.short_link.user.application.write;

import com.example.short_link.common.event.FollowRequestSettledEvent;
import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.FollowRequestRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlockUseCase {

  private final UserRepository userRepository;
  private final BlockRepository blockRepository;
  private final FollowRepository followRepository;
  private final FollowRequestRepository followRequests;
  private final ApplicationEventPublisher events;

  @Transactional
  public void block(Long blockerId, String targetUsername) {
    UserEntity target = resolve(targetUsername);
    if (target.getId().equals(blockerId)) {
      throw new UserException(UserErrorCode.CANNOT_BLOCK_SELF);
    }
    if (!blockRepository.existsByBlockerIdAndBlockedId(blockerId, target.getId())) {
      blockRepository.save(new UserBlockEntity(blockerId, target.getId()));
    }
    followRepository.deleteBetween(blockerId, target.getId());
    withdrawRequest(blockerId, target.getId());
    withdrawRequest(target.getId(), blockerId);
  }

  private void withdrawRequest(Long followerId, Long followingId) {
    if (followRequests.delete(followerId, followingId) > 0) {
      events.publishEvent(new FollowRequestSettledEvent(followingId, followerId, null));
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
