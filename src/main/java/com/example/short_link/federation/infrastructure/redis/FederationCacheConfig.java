package com.example.short_link.federation.infrastructure.redis;

import com.example.short_link.federation.application.NodeInfoService;
import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

@Configuration
public class FederationCacheConfig {

  static final Duration NODEINFO_USAGE_TTL = Duration.ofHours(1);

  @Bean
  public RedisCacheManagerBuilderCustomizer nodeInfoUsageCacheCustomizer(
      RedisCacheConfiguration cacheConfiguration) {
    return builder ->
        builder.withCacheConfiguration(
            NodeInfoService.USAGE_CACHE, cacheConfiguration.entryTtl(NODEINFO_USAGE_TTL));
  }
}
