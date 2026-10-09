package com.example.short_link.federation.infrastructure.redis;

import com.example.short_link.federation.application.inbox.EarlyDeletes;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RedisEarlyDeletes implements EarlyDeletes {

  private static final Duration TTL = Duration.ofHours(6);

  private final StringRedisTemplate redis;

  @Override
  public void remember(Long remoteActorId, String uri) {
    redis.opsForValue().set(key(remoteActorId, uri), "1", TTL);
  }

  @Override
  public boolean remembered(Long remoteActorId, String uri) {
    return Boolean.TRUE.equals(redis.hasKey(key(remoteActorId, uri)));
  }

  private static String key(Long remoteActorId, String uri) {
    return "federation:early-delete:" + remoteActorId + ":" + uri;
  }
}
