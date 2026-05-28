package com.example.short_link.common.config;

import com.example.short_link.common.cache.PolymorphicJsonRedisSerializer;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

  @Bean
  public RedisCacheConfiguration cacheConfiguration(
      @Value("${short-link.cache.key-prefix:}") String keyPrefix) {
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofHours(24))
        .computePrefixWith(cacheName -> keyPrefix + cacheName + "::")
        .serializeKeysWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(
                new PolymorphicJsonRedisSerializer(
                    PolymorphicJsonRedisSerializer.objectMapper("com.example.short_link."))));
  }

  @Override
  public CacheErrorHandler errorHandler() {
    return new CacheErrorHandler() {
      @Override
      public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
        log.warn("cache get failed [{}::{}]: {}", cache.getName(), key, e.getMessage());
      }

      @Override
      public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
        log.warn("cache put failed [{}::{}]: {}", cache.getName(), key, e.getMessage());
      }

      @Override
      public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
        log.warn("cache evict failed [{}::{}]: {}", cache.getName(), key, e.getMessage());
      }

      @Override
      public void handleCacheClearError(RuntimeException e, Cache cache) {
        log.warn("cache clear failed [{}]: {}", cache.getName(), e.getMessage());
      }
    };
  }
}
