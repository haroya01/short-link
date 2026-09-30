package com.example.short_link.user.application.write;

import java.time.Duration;

public interface RefreshTokenStore {
  void save(Long userId, String jti, Duration ttl);

  boolean exists(Long userId, String jti);

  // Removes the live token and leaves its rotation-grace marker as one atomic step. Only the caller
  // that removed the token gets true, and by then every other caller can already see the marker.
  boolean consume(Long userId, String jti, Duration graceTtl);

  void delete(Long userId, String jti);

  void deleteAllForUser(Long userId);

  boolean wasRecentlyRotated(Long userId, String jti);
}
