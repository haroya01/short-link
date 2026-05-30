package com.example.short_link.user.application.write;

import java.time.Duration;

public interface RefreshTokenStore {
  void save(Long userId, String jti, Duration ttl);

  boolean exists(Long userId, String jti);

  void delete(Long userId, String jti);

  void deleteAllForUser(Long userId);

  /** Record that {@code jti} was just rotated, so a replay within {@code graceTtl} is tolerated. */
  void markRotated(Long userId, String jti, Duration graceTtl);

  /** True while a just-rotated {@code jti} is still inside its grace window. */
  boolean wasRecentlyRotated(Long userId, String jti);
}
