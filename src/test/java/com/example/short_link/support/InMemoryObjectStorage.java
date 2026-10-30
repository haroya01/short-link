package com.example.short_link.support;

import com.example.short_link.common.storage.ObjectStorage;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryObjectStorage implements ObjectStorage {

  private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

  public Set<String> keys() {
    return Set.copyOf(objects.keySet());
  }

  @Override
  public boolean isConfigured() {
    return true;
  }

  @Override
  public String presignPut(String key, String contentType, Duration ttl) {
    return "https://upload.test/" + key;
  }

  @Override
  public void putObject(String key, String contentType, byte[] body) {
    objects.put(key, body);
  }

  @Override
  public Optional<Long> objectSize(String key) {
    return Optional.ofNullable(objects.get(key)).map(body -> (long) body.length);
  }

  @Override
  public Optional<byte[]> getObject(String key) {
    return Optional.ofNullable(objects.get(key));
  }

  @Override
  public void applyImmutableCacheControl(String key) {}

  @Override
  public void delete(String key) {
    objects.remove(key);
  }
}
