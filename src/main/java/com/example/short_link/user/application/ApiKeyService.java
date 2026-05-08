package com.example.short_link.user.application;

import com.example.short_link.user.domain.ApiKeyEntity;
import com.example.short_link.user.domain.ApiKeyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

  public static final String KEY_PREFIX = "kurl_";
  private static final String ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static final int RANDOM_LENGTH = 32;

  private final ApiKeyRepository repository;
  private final MeterRegistry meterRegistry;
  private final SecureRandom random = new SecureRandom();

  /** Issues a new key. Raw key is returned ONCE; only the SHA-256 hash is persisted. */
  @Transactional
  public IssuedApiKey issue(Long userId, String name) {
    String raw = KEY_PREFIX + randomString();
    String hash = sha256(raw);
    String shownPrefix = raw.substring(0, Math.min(raw.length(), 12)); // kurl_ + 7 chars
    ApiKeyEntity entity = repository.save(new ApiKeyEntity(userId, shownPrefix, hash, name));
    meterRegistry.counter("api_key.issued").increment();
    return new IssuedApiKey(entity.getId(), raw, shownPrefix, name, entity.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public List<ApiKeySummary> list(Long userId) {
    return repository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(
            e ->
                new ApiKeySummary(
                    e.getId(),
                    e.getKeyPrefix(),
                    e.getName(),
                    e.getLastUsedAt(),
                    e.getCreatedAt(),
                    e.getRevokedAt()))
        .toList();
  }

  @Transactional
  public boolean revoke(Long userId, Long apiKeyId) {
    return repository
        .findById(apiKeyId)
        .filter(e -> e.getUserId().equals(userId))
        .map(
            e -> {
              if (e.isActive()) e.revoke();
              return true;
            })
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public Optional<ApiKeyEntity> resolve(String rawKey) {
    if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) return Optional.empty();
    return repository.findByKeyHash(sha256(rawKey)).filter(ApiKeyEntity::isActive);
  }

  @Async
  @Transactional
  public void recordUsage(Long apiKeyId) {
    repository.findById(apiKeyId).ifPresent(e -> e.markUsed(Instant.now()));
  }

  private String randomString() {
    StringBuilder sb = new StringBuilder(RANDOM_LENGTH);
    for (int i = 0; i < RANDOM_LENGTH; i++) {
      sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }

  static String sha256(String value) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(64);
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public record IssuedApiKey(
      Long id, String rawKey, String prefix, String name, Instant createdAt) {}

  public record ApiKeySummary(
      Long id,
      String prefix,
      String name,
      Instant lastUsedAt,
      Instant createdAt,
      Instant revokedAt) {
    public boolean active() {
      return revokedAt == null;
    }
  }
}
