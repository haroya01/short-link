package com.example.short_link.note.application.write;

import com.example.short_link.common.storage.ImageUploadPolicy;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.ObjectStoragePublicUrls;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Clients upload straight to storage with a presigned PUT, then name the key when posting. A key
// is accepted only under the poster's own prefix and only once storage reports its size.
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteImages {

  static final String KEY_PREFIX = "note-images/";

  private static final Map<String, String> EXTENSIONS =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp",
          "image/gif", "gif");

  private final ImageUploadPolicy policy;
  private final ObjectStorage storage;
  private final ObjectStoragePublicUrls publicUrls;

  public PresignedImage presign(Long userId, String contentType) {
    requireStorage();
    String type = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String extension = EXTENSIONS.get(type);
    if (extension == null) {
      throw new NoteException(
          NoteErrorCode.NOTE_IMAGE_INVALID, "contentType must be one of " + EXTENSIONS.keySet());
    }
    String key = KEY_PREFIX + userId + "/" + UUID.randomUUID() + "." + extension;
    return new PresignedImage(
        storage.presignPut(key, type, Duration.ofSeconds(policy.presignTtlSeconds())),
        key,
        publicUrls.forKey(key),
        policy.maxBytes());
  }

  StoredImage verify(Long userId, NoteDraft.Image image) {
    requireStorage();
    String key = image.key();
    String contentType = key == null ? null : contentTypeOf(key);
    if (key == null || !key.startsWith(KEY_PREFIX + userId + "/") || contentType == null) {
      throw new NoteException(NoteErrorCode.NOTE_IMAGE_INVALID, "image key not owned by user");
    }
    long size =
        storage
            .objectSize(key)
            .orElseThrow(
                () -> new NoteException(NoteErrorCode.NOTE_IMAGE_INVALID, "upload not found"));
    if (size > policy.maxBytes()) {
      deleteQuietly(key);
      throw new NoteException(NoteErrorCode.NOTE_IMAGE_INVALID, "image exceeds maxBytes");
    }
    String alt = image.altText() == null ? null : image.altText().strip();
    if (alt != null && alt.codePointCount(0, alt.length()) > NoteMediaEntity.MAX_ALT_TEXT_LENGTH) {
      throw new NoteException(
          NoteErrorCode.NOTE_ALT_TEXT_TOO_LONG, NoteMediaEntity.MAX_ALT_TEXT_LENGTH);
    }
    storage.applyImmutableCacheControl(key);
    return new StoredImage(
        key,
        publicUrls.forKey(key),
        contentType,
        alt == null || alt.isEmpty() ? null : alt,
        image.width(),
        image.height());
  }

  void deleteQuietly(String key) {
    try {
      storage.delete(key);
    } catch (ObjectStorageException e) {
      log.warn("failed to delete note image key={}", key, e);
    }
  }

  private void requireStorage() {
    if (!storage.isConfigured()) {
      throw new NoteException(NoteErrorCode.NOTE_IMAGES_UNAVAILABLE);
    }
  }

  private static String contentTypeOf(String key) {
    int dot = key.lastIndexOf('.');
    String extension = dot < 0 ? "" : key.substring(dot + 1);
    return EXTENSIONS.entrySet().stream()
        .filter(entry -> entry.getValue().equals(extension))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  public record PresignedImage(String uploadUrl, String key, String publicUrl, long maxBytes) {}

  record StoredImage(
      String key, String url, String contentType, String altText, Integer width, Integer height) {
    StoredImage(String key, String url, String contentType, String altText) {
      this(key, url, contentType, altText, null, null);
    }
  }
}
