package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserMuteEntity;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MuteQueryService {

  private final UserRepository userRepository;
  private final MuteRepository muteRepository;
  private final Clock clock;

  public List<MutedUserView> myMutes(Long userId) {
    List<UserMuteEntity> mutes = muteRepository.active(userId, clock.instant());
    if (mutes.isEmpty()) {
      return List.of();
    }
    Map<Long, UserEntity> byId =
        userRepository
            .findAllByIdIn(mutes.stream().map(UserMuteEntity::getMutedUserId).toList())
            .stream()
            .filter(u -> u.getUsername() != null)
            .collect(Collectors.toMap(UserEntity::getId, u -> u));
    return mutes.stream()
        .map(
            mute -> {
              UserEntity user = byId.get(mute.getMutedUserId());
              return user == null
                  ? null
                  : new MutedUserView(
                      user.getId(),
                      user.getUsername(),
                      user.getAvatarUrl(),
                      mute.isHideNotifications(),
                      mute.getExpiresAt());
            })
        .filter(Objects::nonNull)
        .toList();
  }
}
