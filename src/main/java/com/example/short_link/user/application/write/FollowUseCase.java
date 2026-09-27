package com.example.short_link.user.application.write;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.user.application.read.FollowStatus;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowUseCase {

  private final UserRepository userRepository;
  private final FollowRepository followRepository;
  private final BlockRepository blockRepository;
  private final ApplicationEventPublisher events;

  @Transactional
  public FollowStatus follow(Long followerId, String targetUsername, Long sourcePostId) {
    UserEntity target = requireUser(targetUsername);
    if (target.getId().equals(followerId)) {
      throw new UserException(UserErrorCode.CANNOT_FOLLOW_SELF);
    }
    if (blockRepository.existsByBlockerIdAndBlockedId(target.getId(), followerId)) {
      throw new UserException(UserErrorCode.BLOCKED_TARGET);
    }
    // Only a new follow records attribution and notifies; repeats preserve the original source.
    if (!followRepository.existsByFollowerIdAndFollowingId(followerId, target.getId())) {
      followRepository.save(new FollowEntity(followerId, target.getId(), sourcePostId));
      events.publishEvent(BlogInteractionEvent.follow(target.getId(), followerId, Instant.now()));
    }
    return statusOf(true, target);
  }

  @Transactional
  public FollowStatus unfollow(Long followerId, String targetUsername) {
    UserEntity target = requireUser(targetUsername);
    followRepository
        .findByFollowerIdAndFollowingId(followerId, target.getId())
        .ifPresent(followRepository::delete);
    return statusOf(false, target);
  }

  private FollowStatus statusOf(boolean following, UserEntity target) {
    if (target.isHideFollowerCount()) {
      return FollowStatus.hidden(following);
    }
    return FollowStatus.visible(
        following,
        followRepository.countByFollowingId(target.getId()),
        followRepository.countByFollowerId(target.getId()));
  }

  private UserEntity requireUser(String username) {
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
  }
}
