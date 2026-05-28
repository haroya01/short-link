package com.example.short_link.common.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

public class PolymorphicJsonRedisSerializer implements RedisSerializer<Object> {

  private final ObjectMapper objectMapper;

  public PolymorphicJsonRedisSerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @SuppressWarnings("deprecation")
  public static ObjectMapper objectMapper(String... allowedSubTypes) {
    BasicPolymorphicTypeValidator.Builder builder = BasicPolymorphicTypeValidator.builder();
    for (String allowedSubType : allowedSubTypes) {
      builder.allowIfSubType(allowedSubType);
    }
    PolymorphicTypeValidator ptv =
        builder
            .allowIfSubType("java.util.")
            .allowIfSubType("java.time.")
            .allowIfSubType("java.lang.")
            .allowIfSubType("org.springframework.cache.support.NullValue")
            .build();
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    var typeResolver =
        ObjectMapper.DefaultTypeResolverBuilder.construct(
                ObjectMapper.DefaultTyping.EVERYTHING, ptv)
            .init(JsonTypeInfo.Id.CLASS, null)
            .inclusion(JsonTypeInfo.As.PROPERTY);
    mapper.setDefaultTyping(typeResolver);
    return mapper;
  }

  @Override
  public byte[] serialize(Object value) throws SerializationException {
    if (value == null) return new byte[0];
    try {
      return objectMapper.writeValueAsBytes(value);
    } catch (JsonProcessingException e) {
      throw new SerializationException("Could not write JSON", e);
    }
  }

  @Override
  public Object deserialize(byte[] bytes) throws SerializationException {
    if (bytes == null || bytes.length == 0) return null;
    try {
      return objectMapper.readValue(bytes, Object.class);
    } catch (IOException e) {
      throw new SerializationException("Could not read JSON", e);
    }
  }
}
