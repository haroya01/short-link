package com.example.short_link.admin.application;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class AdminCacheConfig {

  static final String OVERVIEW_CACHE = "admin-overview";

  // EVERYTHING typing is intentional: AdminCohort/AdminLifecycle/AdminActiveUsers are records
  // (final), so NON_FINAL skipped them and they round-tripped back as LinkedHashMap, breaking the
  // Cacheable return type. The PTV gates the type-id property to our admin records + JDK
  // containers.
  static ObjectMapper buildOverviewObjectMapper() {
    PolymorphicTypeValidator ptv =
        BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.example.short_link.admin.application.")
            .allowIfSubType("java.util.")
            .allowIfSubType("java.time.")
            .allowIfSubType("java.lang.")
            .build();
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .activateDefaultTyping(
            ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
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
                    new GenericJackson2JsonRedisSerializer(buildOverviewObjectMapper())));
    return builder -> builder.withCacheConfiguration(OVERVIEW_CACHE, cfg);
  }
}
