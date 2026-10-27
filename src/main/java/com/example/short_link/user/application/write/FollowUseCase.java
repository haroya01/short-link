package com.example.short_link.user.application.write;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.FollowRequestSettledEvent;
import com.example.short_link.common.event.FollowRequestedEvent;
import com.example.short_link.common.user.BlockRelation;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.user.application.read.FollowStatus;
import com.example.short_link.user.domain.FollowEdge;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.FollowRequestEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowListReader;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.FollowRequestRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowUseCase {

  private final UserRepository userRepository;
  private final FollowRepository followRepository;
  private final FollowRequestRepository followRequests;
  private final FollowListReader followLists;
  private final UserBlockChecker userBlocks;
  private final ApplicationEventPublisher events;

  @Transactional
  public FollowStatus follow(Long followerId, String targetUsername, Long sourcePostId) {
    UserEntity target =
        userRepository
            .findByUsername(targetUsername)
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    if (target.getId().equals(followerId)) {
      throw new UserException(UserErrorCode.CANNOT_FOLLOW_SELF);
    }
    BlockRelation blocks = userBlocks.between(followerId, target.getId());
    if (blocks.blocksViewer()) {
      throw new UserException(UserErrorCode.BLOCKED_TARGET);
    }
    FollowEdge edge = FollowEdge.follower(followerId).following(target.getId());
    Optional<FollowEntity> existing = followRepository.find(edge);
    if (existing.isPresent()) {
      return statusOf(true, target, blocks).notifyingOfNotes(existing.get().isNotifyNotes());
    }
    // A locked account approves each follower, as on Mastodon: the follow waits as a request.
    if (target.isLocked()) {
      if (!followRequests.exists(edge)) {
        followRequests.save(new FollowRequestEntity(edge));
        events.publishEvent(new FollowRequestedEvent(target.getId(), followerId, null));
      }
      return statusOf(false, target, blocks).requesting(true, true);
    }
    // Only a new follow records attribution and notifies; repeats preserve the original source.
    FollowEntity follow = new FollowEntity(edge, sourcePostId);
    followRepository.save(follow);
    events.publishEvent(BlogInteractionEvent.follow(target.getId(), followerId, Instant.now()));
    return statusOf(true, target, blocks).notifyingOfNotes(follow.isNotifyNotes());
  }

  @Transactional
  public NoteNotifications setNoteNotifications(
      Long followerId, String targetUsername, boolean on) {
    UserEntity target = requireUser(targetUsername);
    FollowEntity follow =
        followRepository
            .find(FollowEdge.follower(followerId).following(target.getId()))
            .orElseThrow(() -> new UserException(UserErrorCode.NOT_FOLLOWING));
    follow.notifyOfNotes(on);
    followRepository.save(follow);
    return new NoteNotifications(on);
  }

  public record NoteNotifications(boolean notifyNotes) {}

  @Transactional
  public FollowStatus unfollow(Long followerId, String targetUsername) {
    UserEntity target = requireUser(targetUsername);
    FollowEdge edge = FollowEdge.follower(followerId).following(target.getId());
    Optional<FollowEntity> follow = followRepository.find(edge);
    if (follow.isPresent()) {
      followRepository.delete(follow.get());
    } else if (target.isLocked() && followRequests.delete(edge) > 0) {
      events.publishEvent(new FollowRequestSettledEvent(target.getId(), followerId, null));
    }
    return statusOf(false, target, userBlocks.between(followerId, target.getId()));
  }

  private FollowStatus statusOf(boolean following, UserEntity target, BlockRelation blocks) {
    FollowStatus status =
        target.isHideFollowerCount()
            ? FollowStatus.hidden(following)
            : FollowStatus.visible(following, followLists.counts(target.getId()));
    return status.requesting(false, target.isLocked()).blocking(blocks);
  }

  private UserEntity requireUser(String username) {
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
  }
}
