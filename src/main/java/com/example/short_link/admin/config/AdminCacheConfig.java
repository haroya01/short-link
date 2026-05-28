package com.example.short_link.admin.config;

import com.example.short_link.common.cache.PolymorphicJsonRedisSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class AdminCacheConfig {

  static final String OVERVIEW_CACHE = "admin-overview";

  public static ObjectMapper buildOverviewObjectMapper() {
    return PolymorphicJsonRedisSerializer.objectMapper("com.example.short_link.admin.application.");
  }

  @Bean
  public RedisCacheManagerBuilderCustomizer adminOverviewCacheCustomizer() {
    RedisCacheConfiguration cfg =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new PolymorphicJsonRedisSerializer(buildOverviewObjectMapper())));
    return builder -> builder.withCacheConfiguration(OVERVIEW_CACHE, cfg);
  }
}
