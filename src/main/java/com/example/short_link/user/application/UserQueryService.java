package com.example.short_link.user.application;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

  private final UserRepository userRepository;

  /** 활성(soft-delete 아님) user 만 반환. 없거나 deleted 면 throw. */
  public UserEntity activeOrThrow(Long userId) {
    return userRepository
        .findById(userId)
        .filter(u -> !u.isDeleted())
        .orElseThrow(UserNotFoundException::new);
  }

  /** Filter 등 비-throw 경로용. deleted 도 포함된 row 가 그대로 필요한 곳은 직접 repository 사용 X. */
  public Optional<UserEntity> findActive(Long userId) {
    return userRepository.findById(userId).filter(u -> !u.isDeleted());
  }
}
