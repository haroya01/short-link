package com.example.short_link.notification.domain.policy;

import java.util.List;

public enum KeywordVerdict {
  CLEAN,
  WARN,
  HIDE;

  public static KeywordVerdict of(List<KeywordFilter> filters, String text) {
    if (text == null || text.isBlank()) {
      return CLEAN;
    }
    KeywordVerdict verdict = CLEAN;
    for (KeywordFilter filter : filters) {
      if (filter.matches(text)) {
        if (filter.hides()) {
          return HIDE;
        }
        verdict = WARN;
      }
    }
    return verdict;
  }
}
