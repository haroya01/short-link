package com.example.short_link.federation.domain.topics;

import java.text.Normalizer;
import java.util.Locale;

public record TopicArticle(String url, String title, String source, String image) {

  public static final TopicArticle NONE = new TopicArticle(null, null, null, null);

  private static final String SEPARATORS = "|-–—:·";

  public static String withoutSource(String title, String source) {
    if (title == null || source == null) {
      return title;
    }
    String name = folded(source.strip());
    for (int from = title.length() - 1; from > 0; from--) {
      String tail = folded(title.substring(from));
      if (tail.length() > name.length()) {
        break;
      }
      if (tail.equals(name)) {
        return beforeSeparator(title, from);
      }
    }
    return title;
  }

  private static String beforeSeparator(String title, int sourceAt) {
    String head = title.substring(0, sourceAt).stripTrailing();
    if (head.isEmpty()) {
      return title;
    }
    String last = Normalizer.normalize(head.substring(head.length() - 1), Normalizer.Form.NFKC);
    if (last.length() != 1 || SEPARATORS.indexOf(last.charAt(0)) < 0) {
      return title;
    }
    String headline = head.substring(0, head.length() - 1).stripTrailing();
    return headline.isEmpty() ? title : headline;
  }

  private static String folded(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
  }
}
