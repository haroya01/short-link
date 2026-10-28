package com.example.short_link.post.domain;

public final class DiscoveryQuality {

  public static final int MIN_BODY_TEXT_LENGTH = 100;

  private static final int MIN_LABEL_LENGTH = 2;

  private DiscoveryQuality() {}

  public static int meaningfulLength(String text) {
    if (text == null) {
      return 0;
    }
    int count = 0;
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      i += Character.charCount(cp);
      if (!isHangulJamo(cp) && Character.isLetterOrDigit(cp)) {
        count++;
      }
    }
    return count;
  }

  public static boolean isMeaningfulLabel(String label) {
    return meaningfulLength(label) >= MIN_LABEL_LENGTH;
  }

  private static boolean isHangulJamo(int cp) {
    return (cp >= 0x1100 && cp <= 0x11FF)
        || (cp >= 0x3130 && cp <= 0x318F)
        || (cp >= 0xA960 && cp <= 0xA97F)
        || (cp >= 0xD7B0 && cp <= 0xD7FF);
  }
}
