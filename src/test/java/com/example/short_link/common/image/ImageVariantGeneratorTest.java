package com.example.short_link.common.image;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.image.ImageVariantLedger.Outcome;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import java.util.Optional;
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
  void aStorageOutageLeavesNoRecordSoTheSweepTriesAgain() {
    when(storage.getObject(KEY)).thenReturn(Optional.of(ImageFixtures.photo(800, 600)));
    org.mockito.Mockito.doThrow(new ObjectStorageException("down", null))
        .when(storage)
        .putObject(eq("note-images/7/a.w480.jpg"), anyString(), any());

    generator.generate(KEY);

    verify(ledger, never()).record(eq(KEY), any());
  }
}
