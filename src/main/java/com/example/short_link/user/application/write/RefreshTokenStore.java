package com.example.short_link.user.application.write;

import java.time.Duration;

public interface RefreshTokenStore {
  void save(Long userId, String jti, Duration ttl);

  boolean exists(Long userId, String jti);

  void delete(Long userId, String jti);

  void deleteAllForUser(Long userId);
}
