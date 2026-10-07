package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    Optional<FollowEntity> follow =
        viewerId == null
            ? Optional.empty()
            : followRepository.findByFollowerIdAndFollowingId(viewerId, target.getId());
    boolean following = follow.isPresent();
    boolean notifyNotes = follow.map(FollowEntity::isNotifyNotes).orElse(false);
    if (target.isHideFollowerCount()) {
      return FollowStatus.hidden(following).notifyingOfNotes(notifyNotes);
    }
    return FollowStatus.visible(
            following,
            followRepository.countByFollowingId(target.getId()),
            followRepository.countByFollowerId(target.getId()))
        .notifyingOfNotes(notifyNotes);
  }
}
