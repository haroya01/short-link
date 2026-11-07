package com.example.short_link.note.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

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
  private final Logger logger = (Logger) LoggerFactory.getLogger(RemoteMediaCopier.class);
  private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

  @BeforeEach
  void listen() {
    logged.start();
    logger.addAppender(logged);
  }

  @AfterEach
  void stopListening() {
    logger.detachAppender(logged);
  }

  private List<String> lines() {
    return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  private String outcomeOf(String line) {
    return line.substring(line.indexOf("outcome=") + 8, line.indexOf(" ms="));
  }

  @Test
  void aCopyAlreadyKeptIsShownWithoutFetchingAgain() {
    when(copies.kept(KEY)).thenReturn(true);

    copier.copy(KEY, IMAGE, List.of(10L, 11L));

    verify(copies).attach(KEY, IMAGE.url(), COPY, List.of(10L, 11L), NOW);
    verify(fetcher, never()).fetch(anyString(), anyInt());
    verify(variants, never()).storeWithVariants(anyString(), any(), anyLong());
  }

  @Test
  void aFetchedImageIsStoredWithItsCopiesBeforeTheRowsShowIt() {
    when(fetcher.fetch(IMAGE.url(), 1000)).thenReturn(new Image(BYTES, "image/png"));
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
  void aNoteThatSaidJpegButWasServedAPngIsStoredUnderAPngKey() {
    RemoteImage saidJpeg =
        new RemoteImage(12L, "https://files.example/original/b.jpg", "image/jpeg");
    String asked = RemoteMediaKeys.keyOf(saidJpeg.url(), "image/jpeg");
    String stored = RemoteMediaKeys.keyOf(saidJpeg.url(), "image/png");
    when(fetcher.fetch(saidJpeg.url(), 1000)).thenReturn(new Image(BYTES, "image/png"));
    when(variants.storeWithVariants(stored, BYTES, RemoteMediaCopier.MAX_SOURCE_PIXELS))
        .thenReturn(true);
    when(copies.attach(stored, saidJpeg.url(), "https://cdn.example/" + stored, List.of(12L), NOW))
        .thenReturn(1);

    copier.copy(asked, saidJpeg, List.of(12L));

    verify(variants).storeWithVariants(stored, BYTES, RemoteMediaCopier.MAX_SOURCE_PIXELS);
    verify(copies)
        .attach(stored, saidJpeg.url(), "https://cdn.example/" + stored, List.of(12L), NOW);
    verify(variants, never()).storeWithVariants(eq(asked), any(), anyLong());
  }

  @Test
  void aCopyKeptUnderTheOtherTypeIsShownWithoutFetchingAgain() {
    RemoteImage saidJpeg =
        new RemoteImage(12L, "https://files.example/original/b.jpg", "image/jpeg");
    String stored = RemoteMediaKeys.keyOf(saidJpeg.url(), "image/png");
    when(copies.kept(stored)).thenReturn(true);

    copier.copy(RemoteMediaKeys.keyOf(saidJpeg.url(), "image/jpeg"), saidJpeg, List.of(12L));

    verify(copies)
        .attach(stored, saidJpeg.url(), "https://cdn.example/" + stored, List.of(12L), NOW);
    verify(fetcher, never()).fetch(anyString(), anyInt());
  }

  @Test
  void aRefusedOrUndecodableImageLeavesTheRowsOnTheOtherServer() {
    when(fetcher.fetch(IMAGE.url(), 1000)).thenReturn(new Refused("HTTP 404"));
    copier.copy(KEY, IMAGE, List.of(10L));
    verify(variants, never()).storeWithVariants(anyString(), any(), anyLong());

    when(fetcher.fetch(IMAGE.url(), 1000)).thenReturn(new Image(BYTES, "image/png"));
    when(variants.storeWithVariants(KEY, BYTES, RemoteMediaCopier.MAX_SOURCE_PIXELS))
        .thenReturn(false);
    copier.copy(KEY, IMAGE, List.of(10L));

    verify(copies, never()).attach(anyString(), anyString(), anyString(), any(), any());
    verify(storage, never()).deleteWithVariants(anyString());
  }

  @Test
  void aCopyWhoseRowsWentWhileItWasMadeIsDeletedAgain() {
    when(fetcher.fetch(IMAGE.url(), 1000)).thenReturn(new Image(BYTES, "image/png"));
    when(variants.storeWithVariants(KEY, BYTES, RemoteMediaCopier.MAX_SOURCE_PIXELS))
        .thenReturn(true);
    when(copies.attach(KEY, IMAGE.url(), COPY, List.of(10L), NOW)).thenReturn(0);

    copier.copy(KEY, IMAGE, List.of(10L));

    verify(storage).deleteWithVariants(KEY);
    verify(ledger).forget(KEY);
  }

  @Test
  void aFailureAfterStoringDeletesWhatWasStoredAndStaysInTheWorker() {
    when(fetcher.fetch(IMAGE.url(), 1000)).thenReturn(new Image(BYTES, "image/png"));
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

  @Test
  void eachCopyLeavesOneLineNamingTheKeyTheHostAndWhatHappened() {
    when(copies.kept(KEY)).thenReturn(true, false);
    when(copies.attach(KEY, IMAGE.url(), COPY, List.of(10L), NOW)).thenReturn(2, 1, 0);
    copier.copy(KEY, IMAGE, List.of(10L));

    when(fetcher.fetch(IMAGE.url(), 1000))
        .thenReturn(new Refused("HTTP 404"), new Image(BYTES, "image/png"));
    copier.copy(KEY, IMAGE, List.of(10L));

    when(variants.storeWithVariants(KEY, BYTES, RemoteMediaCopier.MAX_SOURCE_PIXELS))
        .thenReturn(false, true, true)
        .thenThrow(new ObjectStorageException("down", null));
    copier.copy(KEY, IMAGE, List.of(10L));
    when(fetcher.fetch(IMAGE.url(), 1000)).thenReturn(new Image(BYTES, "image/png"));
    copier.copy(KEY, IMAGE, List.of(10L));
    copier.copy(KEY, IMAGE, List.of(10L));
    copier.copy(KEY, IMAGE, List.of(10L));

    assertThat(lines())
        .allSatisfy(
            line ->
                assertThat(line).startsWith("remote-media key=" + KEY + " host=files.example "));
    assertThat(lines().stream().map(this::outcomeOf))
        .containsExactly(
            "linked rows=2",
            "refused HTTP 404",
            "undecodable",
            "made rows=1",
            "gone",
            "failed ObjectStorageException");
  }

  @Test
  void anAddressThatIsNotAUrlIsLoggedWithAnUnknownHost() {
    copier.copy(KEY, new RemoteImage(10L, "https://bad host/a.png", "image/png"), List.of(10L));

    assertThat(lines()).singleElement().asString().contains(" host=? ");
  }

  @Test
  void theSweepCountsOnlyTheCopiesItDeletedAndSaysNothingWhenThereWereNone() {
    when(copies.expired(NOW.minus(Duration.ofDays(30)), 50)).thenReturn(List.of("k1", "k2"));
    when(copies.orphaned(50)).thenReturn(List.of("k3"));
    doThrow(new ObjectStorageException("down", null)).when(storage).deleteWithVariants("k2");

    copier.sweep();

    assertThat(lines()).contains("remote-media sweep expired=1 orphaned=1");

    logged.list.clear();
    when(copies.expired(any(), anyInt())).thenReturn(List.of());
    when(copies.orphaned(anyInt())).thenReturn(List.of());
    copier.sweep();

    assertThat(lines()).isEmpty();
  }

  @Test
  void aSweepThatCannotReadItsListsIsLoggedAndDoesNotThrow() {
    when(copies.expired(any(), anyInt())).thenThrow(new IllegalStateException("db down"));

    copier.sweep();

    assertThat(lines()).containsExactly("remote-media sweep failed");
  }

  @ParameterizedTest(name = "queue={0} maxBytes={1} sweepBatch={2}")
  @CsvSource(
      textBlock =
          """
          -1, -1, -1, 100, 10485760, 200
           0,  0,  0, 100, 10485760, 200
           1,  1,  1,   1,        1,   1
          """)
  void aSettingLeftAtZeroOrBelowFallsBackToItsDefaultAndOneIsKept(
      int queue, int maxBytes, int sweepBatch, int wantQueue, int wantMax, int wantBatch) {
    RemoteMediaProperties props =
        new RemoteMediaProperties(true, queue, maxBytes, Duration.ofDays(1), sweepBatch);

    assertThat(props.queue()).isEqualTo(wantQueue);
    assertThat(props.maxBytes()).isEqualTo(wantMax);
    assertThat(props.sweepBatch()).isEqualTo(wantBatch);
  }

  @Test
  void anUnsetKeepingTimeIsThirtyDays() {
    assertThat(new RemoteMediaProperties(true, 0, 0, null, 0).keepFor())
        .isEqualTo(Duration.ofDays(30));
    assertThat(new RemoteMediaProperties(true, 0, 0, Duration.ofDays(1), 0).keepFor())
        .isEqualTo(Duration.ofDays(1));
  }
}
