package com.example.short_link.user.application.write;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.user.application.read.FollowStatus;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Follow / unfollow another author (velog 식 구독). Following yourself is rejected. */
@Service
@RequiredArgsConstructor
public class FollowUseCase {

  private final UserRepository userRepository;
  private final FollowRepository followRepository;
  private final ApplicationEventPublisher events;

  @Transactional
  public FollowStatus follow(Long followerId, String targetUsername, Long sourcePostId) {
    UserEntity target = requireUser(targetUsername);
    if (target.getId().equals(followerId)) {
      throw new UserException(UserErrorCode.CANNOT_FOLLOW_SELF);
    }
    // Attribute only the edge that actually gets created — a re-follow of someone you already
    // follow leaves the original (and its original source) untouched, so the metric isn't inflated
    // by repeats, and only a genuinely new follow notifies the author.
    if (!followRepository.existsByFollowerIdAndFollowingId(followerId, target.getId())) {
      followRepository.save(new FollowEntity(followerId, target.getId(), sourcePostId));
      events.publishEvent(BlogInteractionEvent.follow(target.getId(), followerId, Instant.now()));
    }
    return statusOf(true, target.getId());
  }

  @Transactional
  public FollowStatus unfollow(Long followerId, String targetUsername) {
    UserEntity target = requireUser(targetUsername);
    followRepository
        .findByFollowerIdAndFollowingId(followerId, target.getId())
        .ifPresent(followRepository::delete);
    return statusOf(false, target.getId());
  }

  private FollowStatus statusOf(boolean following, Long targetId) {
    return new FollowStatus(
        following,
        followRepository.countByFollowingId(targetId),
        followRepository.countByFollowerId(targetId));
  }

  private UserEntity requireUser(String username) {
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
  }
}
