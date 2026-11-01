package com.example.short_link.note.application.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.image.ImageVariantGenerator;
import com.example.short_link.common.image.ImageVariantLedger;
import com.example.short_link.common.net.PublicImageFetcher;
import com.example.short_link.common.net.PublicImageFetcher.Image;
import com.example.short_link.common.net.PublicImageFetcher.Refused;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.ObjectStoragePublicUrls;
import com.example.short_link.common.storage.s3.S3StorageProperties;
import com.example.short_link.note.domain.repository.RemoteMediaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemoteMediaCopierTest {

  private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");
  private static final RemoteImage IMAGE =
      new RemoteImage(10L, "https://files.example/original/a.png", "image/png");
  private static final String KEY = RemoteMediaKeys.keyOf(IMAGE.url(), IMAGE.type());
  private static final String COPY = "https://cdn.example/" + KEY;
  private static final byte[] BYTES = {1, 2, 3};

  private final PublicImageFetcher fetcher = mock(PublicImageFetcher.class);
  private final ImageVariantGenerator variants = mock(ImageVariantGenerator.class);
  private final ImageVariantLedger ledger = mock(ImageVariantLedger.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final RemoteMediaRepository copies = mock(RemoteMediaRepository.class);
  private final RemoteMediaCopier copier =
      new RemoteMediaCopier(
          fetcher,
          variants,
          ledger,
          storage,
          new ObjectStoragePublicUrls(
              new S3StorageProperties("bucket", "ap-northeast-2", "https://cdn.example")),
          copies,
          new RemoteMediaProperties(true, 0, 1000, Duration.ofDays(30), 50),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void aCopyAlreadyKeptIsShownWithoutFetchingAgain() {
    when(copies.kept(KEY)).thenReturn(true);

    copier.copy(KEY, IMAGE, List.of(10L, 11L));

    verify(copies).attach(KEY, IMAGE.url(), COPY, List.of(10L, 11L), NOW);
    verify(fetcher, never()).fetch(anyString(), anyString(), anyInt());
    verify(variants, never()).storeWithVariants(anyString(), any(), anyLong());
  }

  @Test
  void aFetchedImageIsStoredWithItsCopiesBeforeTheRowsShowIt() {
    when(fetcher.fetch(IMAGE.url(), "image/png", 1000)).thenReturn(new Image(BYTES));
    when(variants.storeWithVariants(KEY, BYTES, RemoteMediaCopier.MAX_SOURCE_PIXELS))
        .thenReturn(true);
    when(copies.attach(KEY, IMAGE.url(), COPY, List.of(10L), NOW)).thenReturn(1);

    copier.copy(KEY, IMAGE, List.of(10L));

    var order = org.mockito.Mockito.inOrder(variants, copies);
    order.verify(variants).storeWithVariants(KEY, BYTES, RemoteMediaCopier.MAX_SOURCE_PIXELS);
    order.verify(copies).attach(KEY, IMAGE.url(), COPY, List.of(10L), NOW);
    verify(storage, never()).deleteWithVariants(anyString());
  }

  @Test
  void aRefusedOrUndecodableImageLeavesTheRowsOnTheOtherServer() {
    when(fetcher.fetch(IMAGE.url(), "image/png", 1000)).thenReturn(new Refused("HTTP 404"));
    copier.copy(KEY, IMAGE, List.of(10L));
    verify(variants, never()).storeWithVariants(anyString(), any(), anyLong());

    when(fetcher.fetch(IMAGE.url(), "image/png", 1000)).thenReturn(new Image(BYTES));
    when(variants.storeWithVariants(KEY, BYTES, RemoteMediaCopier.MAX_SOURCE_PIXELS))
        .thenReturn(false);
    copier.copy(KEY, IMAGE, List.of(10L));

    verify(copies, never()).attach(anyString(), anyString(), anyString(), any(), any());
    verify(storage, never()).deleteWithVariants(anyString());
  }

  @Test
  void aCopyWhoseRowsWentWhileItWasMadeIsDeletedAgain() {
    when(fetcher.fetch(IMAGE.url(), "image/png", 1000)).thenReturn(new Image(BYTES));
    when(variants.storeWithVariants(KEY, BYTES, RemoteMediaCopier.MAX_SOURCE_PIXELS))
        .thenReturn(true);
    when(copies.attach(KEY, IMAGE.url(), COPY, List.of(10L), NOW)).thenReturn(0);

    copier.copy(KEY, IMAGE, List.of(10L));

    verify(storage).deleteWithVariants(KEY);
    verify(ledger).forget(KEY);
  }

  @Test
  void aFailureAfterStoringDeletesWhatWasStoredAndStaysInTheWorker() {
    when(fetcher.fetch(IMAGE.url(), "image/png", 1000)).thenReturn(new Image(BYTES));
    when(variants.storeWithVariants(KEY, BYTES, RemoteMediaCopier.MAX_SOURCE_PIXELS))
        .thenThrow(new ObjectStorageException("down", null));

    copier.copy(KEY, IMAGE, List.of(10L));

    verify(storage).deleteWithVariants(KEY);
    verify(copies, never()).attach(anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void theSweepPointsRowsBackBeforeDeletingExpiredAndOrphanedCopies() {
    when(copies.expired(NOW.minus(Duration.ofDays(30)), 50)).thenReturn(List.of("k1"));
    when(copies.orphaned(50)).thenReturn(List.of("k2"));

    copier.sweep();

    var order = org.mockito.Mockito.inOrder(copies, storage, ledger);
    order.verify(copies).detach("k1");
    order.verify(storage).deleteWithVariants("k1");
    order.verify(ledger).forget("k1");
    order.verify(copies).forget("k1");
    order.verify(copies).detach("k2");
    order.verify(storage).deleteWithVariants("k2");
    order.verify(ledger).forget("k2");
    order.verify(copies).forget("k2");
  }

  @Test
  void aCopyThatCouldNotBeDeletedStaysOnRecordForTheNextSweep() {
    when(copies.expired(any(), anyInt())).thenReturn(List.of("k1"));
    doThrow(new ObjectStorageException("down", null)).when(storage).deleteWithVariants("k1");

    copier.sweep();

    verify(copies).detach("k1");
    verify(ledger, never()).forget("k1");
    verify(copies, never()).forget("k1");
  }
}
