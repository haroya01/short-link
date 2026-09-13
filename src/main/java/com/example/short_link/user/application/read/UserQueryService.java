package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.UserEntity;
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
public class UserQueryService {

  private final UserRepository userRepository;

  public UserEntity activeOrThrow(Long userId) {
    return userRepository
        .findById(userId)
        .filter(u -> !u.isDeleted())
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
  }

  public Optional<UserEntity> findActive(Long userId) {
    return userRepository.findById(userId).filter(u -> !u.isDeleted());
  }
}
