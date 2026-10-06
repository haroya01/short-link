package com.example.short_link.common.note;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The web and iOS clients link the same tokens with the same pattern; keep the three in step.
public final class Hashtags {

  public static final int MAX_TAGS = 10;
  public static final int MAX_LENGTH = 40;

  public static final Pattern PATTERN =
      Pattern.compile(
          "(?<![=/)\\p{L}\\p{M}\\p{N}_#])#([\\p{L}\\p{M}\\p{N}_][\\p{L}\\p{M}\\p{N}_·・]*)");

  private static final Pattern LETTER = Pattern.compile("\\p{L}");
  private static final Pattern TRAILING_SEPARATORS = Pattern.compile("[·・]+$");

  private Hashtags() {}

  public static String nameAt(Matcher matcher) {
    return nameAt(matcher, 1);
  }

  public static String nameAt(Matcher matcher, int group) {
    String name = TRAILING_SEPARATORS.matcher(matcher.group(group)).replaceFirst("");
    if (name.length() > MAX_LENGTH || !LETTER.matcher(name).find()) {
      return null;
    }
    return name;
  }

  public static List<String> of(String text) {
    if (text == null || text.indexOf('#') < 0) {
      return List.of();
    }
    Map<String, String> byKey = new LinkedHashMap<>();
    Matcher matcher = PATTERN.matcher(text);
    while (matcher.find() && byKey.size() < MAX_TAGS) {
      String name = nameAt(matcher);
      if (name != null) {
        byKey.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
      }
    }
    return new ArrayList<>(byKey.values());
  }

  public static boolean sameTags(String before, String after) {
    return keys(of(before)).equals(keys(of(after)));
  }

  private static List<String> keys(List<String> tags) {
    return tags.stream().map(tag -> tag.toLowerCase(Locale.ROOT)).sorted().toList();
  }
}
