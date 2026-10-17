package com.example.short_link.common.image;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class ExifOrientation {

  static final int UPRIGHT = 1;

  private static final int ORIENTATION_TAG = 0x0112;

  private ExifOrientation() {}

  static int of(byte[] jpeg) {
    try {
      return read(jpeg);
    } catch (IndexOutOfBoundsException truncated) {
      return UPRIGHT;
    }
  }

  static boolean swapsAxes(int orientation) {
    return orientation >= 5 && orientation <= 8;
  }

  private static int read(byte[] jpeg) {
    if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
      return UPRIGHT;
    }
    int at = 2;
    while (at + 4 <= jpeg.length) {
      if ((jpeg[at] & 0xFF) != 0xFF) {
        return UPRIGHT;
      }
      int marker = jpeg[at + 1] & 0xFF;
      if (marker == 0xFF) {
        at++;
        continue;
      }
      if (marker == 0xDA || marker == 0xD9) {
        return UPRIGHT;
      }
      if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
        at += 2;
        continue;
      }
      int length = ((jpeg[at + 2] & 0xFF) << 8) | (jpeg[at + 3] & 0xFF);
      if (marker == 0xE1 && isExif(jpeg, at + 4, length - 2)) {
        return fromTiff(jpeg, at + 10, length - 8);
      }
      at += 2 + length;
    }
    return UPRIGHT;
  }

  private static boolean isExif(byte[] jpeg, int at, int length) {
    return length >= 6
        && jpeg[at] == 'E'
        && jpeg[at + 1] == 'x'
        && jpeg[at + 2] == 'i'
        && jpeg[at + 3] == 'f'
        && jpeg[at + 4] == 0
        && jpeg[at + 5] == 0;
  }

  private static int fromTiff(byte[] jpeg, int start, int length) {
    ByteBuffer tiff = ByteBuffer.wrap(jpeg, start, Math.min(length, jpeg.length - start)).slice();
    if (tiff.get(0) == 'I' && tiff.get(1) == 'I') {
      tiff.order(ByteOrder.LITTLE_ENDIAN);
    } else if (tiff.get(0) != 'M' || tiff.get(1) != 'M') {
      return UPRIGHT;
    }
    int ifd = tiff.getInt(4);
    int entries = tiff.getShort(ifd) & 0xFFFF;
    for (int i = 0; i < entries; i++) {
      int entry = ifd + 2 + i * 12;
      if ((tiff.getShort(entry) & 0xFFFF) == ORIENTATION_TAG) {
        int value = tiff.getShort(entry + 8) & 0xFFFF;
        return value >= 1 && value <= 8 ? value : UPRIGHT;
      }
    }
    return UPRIGHT;
  }
}
