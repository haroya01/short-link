package com.example.short_link.post.domain;

// 발견 화면(첫 화면·태그·추천·이어지는 것들)의 품질 하한선. 작가 페이지·검색·팔로잉은 이 선을 쓰지 않는다.
public final class DiscoveryQuality {

  // 문장 몇 개. 운영 실측에서 시험 글은 4~12자, 가장 짧은 실제 글은 836자였다.
  public static final int MIN_BODY_TEXT_LENGTH = 100;

  private static final int MIN_LABEL_LENGTH = 2;

  private DiscoveryQuality() {}

  // 글자·숫자만 센다. 자모만 친 것(ㅎㅎ·ㅋㅋ)·공백·기호·마크다운 기호는 세지 않는다.
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
