package com.example.short_link.common.observability;

import com.example.short_link.common.user.UserDataEraser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Retains operational metrics while removing the erased account's identifier. */
@Component
@RequiredArgsConstructor
class RequestMetricsUserDataEraser implements UserDataEraser {
  private final RequestMetricJpaRepository repository;

  @Override
  public void eraseFor(long userId) {
    repository.anonymizeUser(userId);
  }
}
