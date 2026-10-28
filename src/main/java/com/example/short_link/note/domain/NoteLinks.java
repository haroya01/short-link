package com.example.short_link.note.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Trims trailing punctuation the same way the web, iOS and federation renderers do.
public final class NoteLinks {

  private static final Pattern URL = Pattern.compile("https?://[^\\s<]+");
  private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,!?:;)\\]'\"]+$");

  private NoteLinks() {}

  public static String previewUrl(String body, boolean hasMedia, boolean hasQuote) {
    if (hasMedia || hasQuote || body == null) {
      return null;
    }
    Matcher matcher = URL.matcher(body);
    if (!matcher.find()) {
      return null;
    }
    String url = TRAILING_PUNCTUATION.matcher(matcher.group()).replaceFirst("");
    return url.length() > 2048 ? null : url;
  }

  public static boolean mayHavePreview(String body) {
    return body != null && body.contains("://");
  }
}
