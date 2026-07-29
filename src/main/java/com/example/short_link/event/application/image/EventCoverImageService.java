package com.example.short_link.event.application.image;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.s3.AvatarProperties;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 커버 이미지 — {@code PostImageService} 의 presign/commit two-step 축소판 (URL import 없음). key prefix
 * {@code event-covers/{userId}/{eventId}/{uuid}.{ext}}, storage 정책은 AvatarProperties 재사용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventCoverImageService {

  private static final Map<String, String> ALLOWED_TYPES =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp");

  private static final String KEY_PREFIX = "event-covers/";

  private final AvatarProperties props;
  private final ObjectStorage objectStorage;
  private final EventRepository eventRepository;

  public record PresignResult(
      String uploadUrl, String publicUrl, String key, String contentType, long maxBytes) {}

  public PresignResult presignUpload(Long userId, Long eventId, String contentType) {
    requireConfigured();
    requireOwned(userId, eventId);
    String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String ext = ALLOWED_TYPES.get(normalized);
    if (ext == null) {
      throw new EventException(EventErrorCode.INVALID_QUESTIONS, "contentType");
    }
    String key = KEY_PREFIX + userId + "/" + eventId + "/" + UUID.randomUUID() + "." + ext;
    String uploadUrl =
        objectStorage.presignPut(key, normalized, Duration.ofSeconds(props.presignTtlSeconds()));
    return new PresignResult(uploadUrl, urlFor(key), key, normalized, props.maxBytes());
  }

  @Transactional
  public String commitUpload(Long userId, Long eventId, String key) {
    requireConfigured();
    EventEntity event = requireOwned(userId, eventId);
    String expectedPrefix = KEY_PREFIX + userId + "/" + eventId + "/";
    if (key == null || key.isBlank() || !key.startsWith(expectedPrefix)) {
      throw new EventException(EventErrorCode.EVENT_PERMISSION_DENIED);
    }
    long size =
        objectStorage
            .objectSize(key)
            .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND, "upload"));
    if (size > props.maxBytes()) {
      try {
        objectStorage.delete(key);
      } catch (ObjectStorageException e) {
        log.warn("failed to delete oversized event cover key={}", key, e);
      }
      throw new EventException(EventErrorCode.INVALID_QUESTIONS, "image too large");
    }
    objectStorage.applyImmutableCacheControl(key);
    event.updateCoverImage(key);
    return urlFor(key);
  }

  public String urlFor(String key) {
    if (key == null || key.isBlank()) return null;
    String base = props.publicBaseUrl();
    if (base == null || base.isBlank()) {
      base = "https://" + props.bucket() + ".s3." + props.region() + ".amazonaws.com";
    }
    if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    return base + "/" + key;
  }

  private EventEntity requireOwned(Long userId, Long eventId) {
    EventEntity event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND, eventId));
    if (!event.isOwnedBy(userId)) {
      throw new EventException(EventErrorCode.EVENT_PERMISSION_DENIED);
    }
    return event;
  }

  private void requireConfigured() {
    if (!props.isConfigured()) {
      throw new EventException(EventErrorCode.INVALID_QUESTIONS, "storage not configured");
    }
  }
}
