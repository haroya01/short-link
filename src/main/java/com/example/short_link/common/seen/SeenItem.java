package com.example.short_link.common.seen;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SeenItem(int kind, long id) {

  public static final int NOTE = Unseen.NOTE;
  public static final int POST = Unseen.POST;

  private static final Pattern KEY = Pattern.compile("kurl:(note|post):([1-9][0-9]{0,18})");

  public static Optional<SeenItem> sent(String key) {
    if (key == null) {
      return Optional.empty();
    }
    Matcher matched = KEY.matcher(key);
    if (!matched.matches()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new SeenItem(
              matched.group(1).equals("note") ? NOTE : POST, Long.parseLong(matched.group(2))));
    } catch (NumberFormatException overflow) {
      return Optional.empty();
    }
  }
}
