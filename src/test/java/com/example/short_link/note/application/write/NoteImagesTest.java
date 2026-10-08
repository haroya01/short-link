package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ImageUploadPolicy;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.ObjectStoragePublicUrls;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoteImagesTest {

  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final ObjectStoragePublicUrls urls = mock(ObjectStoragePublicUrls.class);
  private final NoteImages images = new NoteImages(new ImageUploadPolicy(300, 100), storage, urls);

  @BeforeEach
  void configured() {
    when(storage.isConfigured()).thenReturn(true);
    when(urls.forKey(anyString())).thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));
  }

  private static NoteErrorCode code(Throwable e) {
    return ((NoteException) e).errorCode();
  }

  @Test
  void presignKeepsKeysUnderThePostersPrefix() {
    when(storage.presignPut(anyString(), eq("image/webp"), any())).thenReturn("https://put");

    NoteImages.PresignedImage presigned = images.presign(7L, " IMAGE/WEBP ");

    assertThat(presigned.key()).startsWith("note-images/7/").endsWith(".webp");
    assertThat(presigned.uploadUrl()).isEqualTo("https://put");
    assertThat(presigned.maxBytes()).isEqualTo(100);
    assertThatThrownBy(() -> images.presign(7L, "image/svg+xml"))
        .satisfies(e -> assertThat(code(e)).isEqualTo(NoteErrorCode.NOTE_IMAGE_INVALID));
    assertThatThrownBy(() -> images.presign(7L, null)).isInstanceOf(NoteException.class);
  }

  @Test
  void withoutStorageImagesAreUnavailable() {
    when(storage.isConfigured()).thenReturn(false);

    assertThatThrownBy(() -> images.presign(7L, "image/png"))
        .satisfies(e -> assertThat(code(e)).isEqualTo(NoteErrorCode.NOTE_IMAGES_UNAVAILABLE));
  }

  @Test
  void anUploadedKeyIsVerifiedAndItsAltTextTrimmed() {
    when(storage.objectSize("note-images/7/a.jpg")).thenReturn(Optional.of(50L));

    NoteImages.StoredImage stored =
        images.verify(7L, new NoteDraft.Image("note-images/7/a.jpg", "  a cat  "));

    assertThat(stored)
        .isEqualTo(
            new NoteImages.StoredImage(
                "note-images/7/a.jpg", "https://cdn/note-images/7/a.jpg", "image/jpeg", "a cat"));
    verify(storage).applyImmutableCacheControl("note-images/7/a.jpg");
    when(storage.objectSize("note-images/7/b.gif")).thenReturn(Optional.of(1L));
    assertThat(images.verify(7L, new NoteDraft.Image("note-images/7/b.gif", "  ")).altText())
        .isNull();
  }

  @Test
  void theSizeTheDeviceShowedTravelsWithTheUpload() {
    when(storage.objectSize("note-images/7/c.jpg")).thenReturn(Optional.of(50L));

    NoteImages.StoredImage stored =
        images.verify(7L, new NoteDraft.Image("note-images/7/c.jpg", null, 1200, 1600));

    assertThat(stored.width()).isEqualTo(1200);
    assertThat(stored.height()).isEqualTo(1600);
  }

  @Test
  void someoneElsesKeyAnUnknownTypeOrAMissingUploadIsRefused() {
    for (String key : new String[] {"note-images/8/a.png", "note-images/7/a.svg", null}) {
      assertThatThrownBy(() -> images.verify(7L, new NoteDraft.Image(key, null)))
          .satisfies(e -> assertThat(code(e)).isEqualTo(NoteErrorCode.NOTE_IMAGE_INVALID));
    }
    when(storage.objectSize("note-images/7/a.png")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> images.verify(7L, new NoteDraft.Image("note-images/7/a.png", null)))
        .isInstanceOf(NoteException.class);
  }

  @Test
  void anOversizedUploadIsDeletedEvenIfDeletionFails() {
    when(storage.objectSize("note-images/7/big.png")).thenReturn(Optional.of(101L));
    doThrow(new ObjectStorageException("down", null)).when(storage).delete("note-images/7/big.png");

    assertThatThrownBy(() -> images.verify(7L, new NoteDraft.Image("note-images/7/big.png", null)))
        .isInstanceOf(NoteException.class);
    verify(storage).delete("note-images/7/big.png");
  }

  @Test
  void altTextIsCappedInCharacters() {
    when(storage.objectSize("note-images/7/a.png")).thenReturn(Optional.of(1L));
    String tooLong = "가".repeat(NoteMediaEntity.MAX_ALT_TEXT_LENGTH + 1);

    assertThatThrownBy(() -> images.verify(7L, new NoteDraft.Image("note-images/7/a.png", tooLong)))
        .satisfies(e -> assertThat(code(e)).isEqualTo(NoteErrorCode.NOTE_ALT_TEXT_TOO_LONG));
  }
}
