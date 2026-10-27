package com.example.short_link.event.application.image;

import com.example.short_link.common.storage.ImageRejections;
import com.example.short_link.common.storage.ImageUploads;
import com.example.short_link.common.storage.PresignedUpload;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventCoverImageService {

  private static final String KEY_PREFIX = "event-covers/";

  private static final ImageRejections REJECTIONS =
      new ImageRejections(
          () -> new EventException(EventErrorCode.INVALID_QUESTIONS, "storage not configured"),
          allowed -> new EventException(EventErrorCode.INVALID_QUESTIONS, "contentType"),
          () -> new EventException(EventErrorCode.EVENT_PERMISSION_DENIED),
          () -> new EventException(EventErrorCode.EVENT_NOT_FOUND, "upload"),
          (size, max) -> new EventException(EventErrorCode.INVALID_QUESTIONS, "image too large"));

  private final ImageUploads uploads;
  private final EventRepository eventRepository;

  public record PresignResult(
      String uploadUrl, String publicUrl, String key, String contentType, long maxBytes) {}

  public PresignResult presignUpload(Long userId, Long eventId, String contentType) {
    uploads.requireConfigured(REJECTIONS);
    requireOwned(userId, eventId);
    PresignedUpload upload =
        uploads.presign(ownedPrefix(userId, eventId), contentType, ImageUploads.STILL, REJECTIONS);
    return new PresignResult(
        upload.uploadUrl(),
        upload.publicUrl(),
        upload.key(),
        upload.contentType(),
        upload.maxBytes());
  }

  @Transactional
  public String commitUpload(Long userId, Long eventId, String key) {
    uploads.requireConfigured(REJECTIONS);
    EventEntity event = requireOwned(userId, eventId);
    String publicUrl = uploads.commit(ownedPrefix(userId, eventId), key, REJECTIONS);
    event.updateCoverImage(key);
    return publicUrl;
  }

  private static String ownedPrefix(Long userId, Long eventId) {
    return KEY_PREFIX + userId + "/" + eventId + "/";
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
}
