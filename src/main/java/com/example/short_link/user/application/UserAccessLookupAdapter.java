package com.example.short_link.user.application;

import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class UserAccessLookupAdapter implements UserAccessLookup {

  private final UserRepository users;

  @Override
  @Transactional(readOnly = true)
  public boolean isAdmin(Long userId) {
    if (userId == null) return false;
    return users.findById(userId).map(UserEntity::isAdmin).orElse(false);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> timezone(Long userId) {
    if (userId == null) return Optional.empty();
    return users.findById(userId).map(user -> user.getTimezone() == null ? "" : user.getTimezone());
  }
}
