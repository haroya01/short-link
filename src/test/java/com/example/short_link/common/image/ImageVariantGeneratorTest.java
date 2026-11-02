package com.example.short_link.common.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.image.ImageVariantLedger.Outcome;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ImageVariantGeneratorTest {

  private static final String KEY = "note-images/7/a.jpg";

  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final ImageVariantLedger ledger = mock(ImageVariantLedger.class);
  private final ImageVariantGenerator generator =
      new ImageVariantGenerator(storage, new ImageVariantRenderer(), ledger);

  @Test
  void writesBothVariantsWithTheOriginalsTypeThenRecordsThem() {
    when(storage.getObject(KEY)).thenReturn(Optional.of(ImageFixtures.photo(1800, 2400)));

    generator.generate(KEY);

    verify(storage).putObject(eq("note-images/7/a.w1080.jpg"), eq("image/jpeg"), any());
    verify(storage).putObject(eq("note-images/7/a.w480.jpg"), eq("image/jpeg"), any());
    verify(ledger).record(KEY, Outcome.MADE);
  }

  @Test
  void aPngIsWrittenAsPng() {
    String key = "post-images/7/42/a.png";
    when(storage.getObject(key))
        .thenReturn(
            Optional.of(
                ImageFixtures.encode(ImageFixtures.decode(ImageFixtures.photo(600, 400)), "png")));

    generator.generate(key);

    verify(storage).putObject(eq("post-images/7/42/a.w1080.png"), eq("image/png"), any());
    verify(storage).putObject(eq("post-images/7/42/a.w480.png"), eq("image/png"), any());
  }

  @Test
  void anOriginalAlreadyDoneIsSkipped() {
    when(ledger.recorded(KEY)).thenReturn(true);

    generator.generate(KEY);

    verify(storage, never()).getObject(anyString());
    verify(storage, never()).putObject(anyString(), anyString(), any());
  }

  @Test
  void aMissingOrUndecodableOriginalIsRecordedAsFailedSoTheSweepStopsAsking() {
    when(storage.getObject(KEY)).thenReturn(Optional.empty());
    generator.generate(KEY);

    String broken = "note-images/7/b.jpg";
    when(storage.getObject(broken)).thenReturn(Optional.of(new byte[] {1, 2, 3}));
    generator.generate(broken);

    verify(ledger).record(KEY, Outcome.FAILED);
    verify(ledger).record(broken, Outcome.FAILED);
    verify(storage, never()).putObject(anyString(), anyString(), any());
  }

  @Test
  void anImageGivenInHandIsStoredWithBothCopiesAndRecorded() {
    String key = "note-images/remote/ab.png";
    byte[] png = ImageFixtures.encode(ImageFixtures.decode(ImageFixtures.photo(1600, 900)), "png");

    assertThat(generator.storeWithVariants(key, png, Long.MAX_VALUE)).isTrue();

    verify(storage).putObject(key, "image/png", png);
    verify(storage).putObject(eq("note-images/remote/ab.w1080.png"), eq("image/png"), any());
    verify(storage).putObject(eq("note-images/remote/ab.w480.png"), eq("image/png"), any());
    verify(ledger).record(key, Outcome.MADE);
    verify(storage, never()).getObject(anyString());
  }

  @Test
  void anImageInHandWithMorePixelsThanAllowedOrNoPictureIsNotStored() {
    String key = "note-images/remote/ab.jpg";

    assertThat(generator.storeWithVariants(key, ImageFixtures.photo(800, 600), 800 * 600 - 1))
        .isFalse();
    assertThat(generator.storeWithVariants(key, new byte[] {1, 2, 3}, Long.MAX_VALUE)).isFalse();

    verify(storage, never()).putObject(anyString(), anyString(), any());
    verify(ledger, never()).record(anyString(), any());
  }

  @Test
  void oneImageIsDecodedAtATimeWhicheverThreadAsks() throws Exception {
    ImageVariantRenderer renderer = mock(ImageVariantRenderer.class);
    ImageVariantGenerator shared = new ImageVariantGenerator(storage, renderer, ledger);
    when(storage.getObject(KEY)).thenReturn(Optional.of(new byte[] {1}));
    CountDownLatch decoding = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicBoolean secondDecoded = new AtomicBoolean();
    doAnswer(
            call -> {
              decoding.countDown();
              release.await(10, TimeUnit.SECONDS);
              throw new java.io.IOException("stop here");
            })
        .when(renderer)
        .render(any(), anyBoolean());
    doAnswer(
            call -> {
              secondDecoded.set(true);
              throw new java.io.IOException("stop here");
            })
        .when(renderer)
        .render(any(), anyBoolean(), anyLong());

    Thread first = Thread.ofPlatform().start(() -> shared.generate(KEY));
    assertThat(decoding.await(5, TimeUnit.SECONDS)).isTrue();
    Thread second =
        Thread.ofPlatform()
            .start(() -> shared.storeWithVariants("note-images/remote/ab.jpg", new byte[] {1}, 1));
    second.join(300);
    assertThat(secondDecoded).isFalse();

    release.countDown();
    first.join(5000);
    second.join(5000);
    assertThat(secondDecoded).isTrue();
  }

  @Test
  void aSlowStorageCallDoesNotHoldUpTheOtherThreadsDecode() throws Exception {
    ImageVariantRenderer renderer = mock(ImageVariantRenderer.class);
    ImageVariantGenerator shared = new ImageVariantGenerator(storage, renderer, ledger);
    CountDownLatch fetching = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicBoolean secondDecoded = new AtomicBoolean();
    when(storage.getObject(KEY))
        .thenAnswer(
            call -> {
              fetching.countDown();
              release.await(10, TimeUnit.SECONDS);
              return Optional.empty();
            });
    doAnswer(
            call -> {
              secondDecoded.set(true);
              throw new java.io.IOException("stop here");
            })
        .when(renderer)
        .render(any(), anyBoolean(), anyLong());

    Thread first = Thread.ofPlatform().start(() -> shared.generate(KEY));
    assertThat(fetching.await(5, TimeUnit.SECONDS)).isTrue();
    Thread second =
        Thread.ofPlatform()
            .start(() -> shared.storeWithVariants("note-images/remote/ab.jpg", new byte[] {1}, 1));
    second.join(5000);

    assertThat(secondDecoded).isTrue();
    release.countDown();
    first.join(5000);
  }

  @Test
  void aStorageOutageLeavesNoRecordSoTheSweepTriesAgain() {
    when(storage.getObject(KEY)).thenReturn(Optional.of(ImageFixtures.photo(800, 600)));
    org.mockito.Mockito.doThrow(new ObjectStorageException("down", null))
        .when(storage)
        .putObject(eq("note-images/7/a.w480.jpg"), anyString(), any());

    generator.generate(KEY);

    verify(ledger, never()).record(eq(KEY), any());
  }
}
