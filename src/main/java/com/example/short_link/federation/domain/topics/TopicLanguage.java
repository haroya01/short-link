package com.example.short_link.federation.domain.topics;

import com.example.short_link.federation.domain.foryou.ForYouProfile;
import java.lang.Character.UnicodeScript;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TopicLanguage {

  public static final int SAMPLED_TAGS = 20;
  public static final int AGREEING_PERCENT = 60;

  private static final Set<UnicodeScript> KANA =
      Set.of(UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA);

  private TopicLanguage() {}

  public static String primary(String tag) {
    return ForYouProfile.language(tag);
  }

  public static String ofScript(String text) {
    if (text == null) {
      return null;
    }
    boolean kana = false;
    for (int at = 0; at < text.length(); at += Character.charCount(text.codePointAt(at))) {
      UnicodeScript script = UnicodeScript.of(text.codePointAt(at));
      if (script == UnicodeScript.HANGUL) {
        return "ko";
      }
      kana = kana || KANA.contains(script);
    }
    return kana ? "ja" : null;
  }

  public static String sampled(Collection<String> stated) {
    Map<String, Integer> counts = new HashMap<>();
    int total = 0;
    for (String tag : stated) {
      String language = primary(tag);
      if (language != null) {
        counts.merge(language, 1, Integer::sum);
        total++;
      }
    }
    int needed = total * AGREEING_PERCENT;
    return counts.entrySet().stream()
        .filter(count -> count.getValue() * 100 >= needed)
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }
}
