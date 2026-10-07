package com.example.short_link.user.application.write;

import com.example.short_link.common.event.AccountUnlockedEvent;
import com.example.short_link.common.event.FollowRequestSettledEvent;
import com.example.short_link.user.application.read.FollowRequestView;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.FollowRequestRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// A locked member's waiting followers: approve one (the request becomes a follow), turn one down,
// or — on unlocking the account — let everyone in, as Mastodon does.
@Service
@RequiredArgsConstructor
public class FollowRequestUseCase {

  static final int PAGE_SIZE = 40;

  private final UserRepository userRepository;
  private final FollowRepository followRepository;
  private final FollowRequestRepository followRequests;
  private final ApplicationEventPublisher events;

  @Transactional(readOnly = true)
  public List<FollowRequestView> pending(Long ownerId, int page) {
    return followRequests.pending(ownerId, Math.max(page, 0) * PAGE_SIZE, PAGE_SIZE).stream()
        .map(FollowRequestView::of)
        .toList();
  }

  @Transactional
  public void authorize(Long ownerId, String username) {
    UserEntity requester = requester(ownerId, username);
    if (!followRepository.existsByFollowerIdAndFollowingId(requester.getId(), ownerId)) {
      followRepository.save(new FollowEntity(requester.getId(), ownerId));
    }
  }

  @Transactional
  public void reject(Long ownerId, String username) {
    requester(ownerId, username);
  }

  @EventListener
  public void onUnlocked(AccountUnlockedEvent event) {
    followRequests.approveAll(event.userId());
  }

  // Removes the request; a request that is not there is a 404 either way.
  private UserEntity requester(Long ownerId, String username) {
    UserEntity requester =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    if (followRequests.delete(requester.getId(), ownerId) == 0) {
      throw new UserException(UserErrorCode.FOLLOW_REQUEST_NOT_FOUND, username);
    }
    events.publishEvent(new FollowRequestSettledEvent(ownerId, requester.getId(), null));
    return requester;
  }
}
