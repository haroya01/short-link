package com.example.short_link.user.application;

import com.example.short_link.common.lock.UserMutationLock;
import com.example.short_link.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class UserMutationLockAdapter implements UserMutationLock {
  private final UserRepository users;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lock(Long userId) {
    users.findByIdForUpdate(userId).orElseThrow(() -> new IllegalArgumentException("unknown user"));
  }
}
