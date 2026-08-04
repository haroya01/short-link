package com.example.short_link.event.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.s3.AvatarProperties;
import com.example.short_link.event.application.image.EventCoverImageService.PresignResult;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventCoverImageServiceTest {

  private static final AvatarProperties CONFIGURED =
      new AvatarProperties("covers", "ap-northeast-2", "https://cdn.kurl.me/", 300, 1024);

  @Mock private ObjectStorage objectStorage;
  @Mock private EventRepository eventRepository;

  private EventCoverImageService service(AvatarProperties props) {
    return new EventCoverImageService(props, objectStorage, eventRepository);
  }

  private EventEntity ownedEvent() {
    EventEntity event =
        new EventEntity(
            1L,
            "slug123abc",
            "스터디",
            null,
            Instant.parse("2026-09-01T10:00:00Z"),
            null,
            "Asia/Seoul",
            null,
            null,
            null,
            null,
            null,
            ContactField.EMAIL);
    ReflectionTestUtils.setField(event, "id", 10L);
    return event;
  }

  @Test
  void unconfiguredStorage_refusesPresign() {
    AvatarProperties blank = new AvatarProperties("", "ap-northeast-2", null, 300, 1024);

    assertThatThrownBy(() -> service(blank).presignUpload(1L, 10L, "image/jpeg"))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.INVALID_QUESTIONS);
  }

  @Test
  void presign_forOthersEvent_isDenied() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));

    assertThatThrownBy(() -> service(CONFIGURED).presignUpload(99L, 10L, "image/jpeg"))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_PERMISSION_DENIED);
  }

  @Test
  void presign_missingEvent_isNotFound() {
    when(eventRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service(CONFIGURED).presignUpload(1L, 10L, "image/jpeg"))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_NOT_FOUND);
  }

  @Test
  void presign_rejectsUnknownContentTypes() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));

    for (String type : new String[] {null, "image/gif", "text/html"}) {
      assertThatThrownBy(() -> service(CONFIGURED).presignUpload(1L, 10L, type))
          .isInstanceOf(EventException.class)
          .extracting(e -> ((EventException) e).errorCode())
          .isEqualTo(EventErrorCode.INVALID_QUESTIONS);
    }
  }

  @Test
  void presign_normalizesTypeAndScopesKeyToOwner() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));
    when(objectStorage.presignPut(anyString(), eq("image/png"), any()))
        .thenReturn("https://upload.example");

    PresignResult result = service(CONFIGURED).presignUpload(1L, 10L, "  IMAGE/PNG ");

    assertThat(result.key()).startsWith("event-covers/1/10/").endsWith(".png");
    assertThat(result.uploadUrl()).isEqualTo("https://upload.example");
    assertThat(result.publicUrl()).isEqualTo("https://cdn.kurl.me/" + result.key());
    assertThat(result.contentType()).isEqualTo("image/png");
    assertThat(result.maxBytes()).isEqualTo(1024);
  }

  @Test
  void commit_rejectsKeyOutsideOwnerPrefix() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));

    for (String key : new String[] {null, "  ", "event-covers/99/10/x.jpg", "avatars/1/x.jpg"}) {
      assertThatThrownBy(() -> service(CONFIGURED).commitUpload(1L, 10L, key))
          .isInstanceOf(EventException.class)
          .extracting(e -> ((EventException) e).errorCode())
          .isEqualTo(EventErrorCode.EVENT_PERMISSION_DENIED);
    }
  }

  @Test
  void commit_withoutUploadedObject_isNotFound() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));
    when(objectStorage.objectSize("event-covers/1/10/a.jpg")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service(CONFIGURED).commitUpload(1L, 10L, "event-covers/1/10/a.jpg"))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_NOT_FOUND);
  }

  @Test
  void oversizedUpload_isDeletedAndRejected() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));
    when(objectStorage.objectSize("event-covers/1/10/a.jpg")).thenReturn(Optional.of(4096L));

    assertThatThrownBy(() -> service(CONFIGURED).commitUpload(1L, 10L, "event-covers/1/10/a.jpg"))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.INVALID_QUESTIONS);
    verify(objectStorage).delete("event-covers/1/10/a.jpg");
  }

  @Test
  void oversizedUpload_stillRejectedWhenCleanupFails() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));
    when(objectStorage.objectSize("event-covers/1/10/a.jpg")).thenReturn(Optional.of(4096L));
    doThrow(new ObjectStorageException("boom", null))
        .when(objectStorage)
        .delete("event-covers/1/10/a.jpg");

    assertThatThrownBy(() -> service(CONFIGURED).commitUpload(1L, 10L, "event-covers/1/10/a.jpg"))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.INVALID_QUESTIONS);
  }

  @Test
  void commit_attachesCoverAndTagsCache() {
    EventEntity event = ownedEvent();
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
    when(objectStorage.objectSize("event-covers/1/10/a.jpg")).thenReturn(Optional.of(512L));

    String url = service(CONFIGURED).commitUpload(1L, 10L, "event-covers/1/10/a.jpg");

    assertThat(url).isEqualTo("https://cdn.kurl.me/event-covers/1/10/a.jpg");
    assertThat(event.getCoverImageKey()).isEqualTo("event-covers/1/10/a.jpg");
    verify(objectStorage).applyImmutableCacheControl("event-covers/1/10/a.jpg");
  }

  @Test
  void urlFor_composesFromBaseOrBucket() {
    assertThat(service(CONFIGURED).urlFor(null)).isNull();
    assertThat(service(CONFIGURED).urlFor("  ")).isNull();
    assertThat(service(CONFIGURED).urlFor("k.jpg")).isEqualTo("https://cdn.kurl.me/k.jpg");

    AvatarProperties noBase = new AvatarProperties("covers", "ap-northeast-2", " ", 300, 1024);
    assertThat(service(noBase).urlFor("k.jpg"))
        .isEqualTo("https://covers.s3.ap-northeast-2.amazonaws.com/k.jpg");
  }
}
