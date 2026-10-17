package com.example.short_link.common.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ExifOrientationTest {

  private static final byte[] PLAIN = ImageFixtures.photo(40, 20);

  @Test
  void readsTheTagWrittenByAnotherEncoder() {
    assertThat(ExifOrientation.of(ImageFixtures.resource("rotated-6.jpg"))).isEqualTo(6);
  }

  @Test
  void readsBothByteOrders() {
    for (int orientation = 1; orientation <= 8; orientation++) {
      assertThat(
              ExifOrientation.of(
                  ImageFixtures.withOrientation(PLAIN, orientation, ByteOrder.BIG_ENDIAN)))
          .isEqualTo(orientation);
      assertThat(
              ExifOrientation.of(
                  ImageFixtures.withOrientation(PLAIN, orientation, ByteOrder.LITTLE_ENDIAN)))
          .isEqualTo(orientation);
    }
  }

  @Test
  void anythingElseIsUpright() {
    assertThat(ExifOrientation.of(PLAIN)).isEqualTo(1);
    assertThat(ExifOrientation.of(ImageFixtures.withOrientation(PLAIN, 9, ByteOrder.BIG_ENDIAN)))
        .isEqualTo(1);
    byte[] rotated = ImageFixtures.withOrientation(PLAIN, 6, ByteOrder.BIG_ENDIAN);
    assertThat(ExifOrientation.of(Arrays.copyOf(rotated, 30))).isEqualTo(1);
    assertThat(ExifOrientation.of(new byte[] {(byte) 0x89, 'P', 'N', 'G'})).isEqualTo(1);
    assertThat(ExifOrientation.of(new byte[0])).isEqualTo(1);
  }

  @Test
  void onlyTheQuarterTurnsSwapWidthAndHeight() {
    assertThat(ExifOrientation.swapsAxes(1)).isFalse();
    assertThat(ExifOrientation.swapsAxes(4)).isFalse();
    assertThat(ExifOrientation.swapsAxes(5)).isTrue();
    assertThat(ExifOrientation.swapsAxes(8)).isTrue();
  }
}
