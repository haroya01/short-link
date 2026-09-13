package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accepts a null viewerId for anonymous reads; their following flag is false. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowQueryService {

  private final UserRepository userRepository;
  private final FollowRepository followRepository;

  public FollowStatus status(Long viewerId, String targetUsername) {
    UserEntity target =
        userRepository
            .findByUsername(targetUsername)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    boolean following =
        viewerId != null
            && followRepository.existsByFollowerIdAndFollowingId(viewerId, target.getId());
    if (target.isHideFollowerCount()) {
      return FollowStatus.hidden(following);
    }
    return FollowStatus.visible(
        following,
        followRepository.countByFollowingId(target.getId()),
        followRepository.countByFollowerId(target.getId()));
  }
}
