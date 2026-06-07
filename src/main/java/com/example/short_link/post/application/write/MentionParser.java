package com.example.short_link.post.application.write;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts {@code @username} handles from a comment body. Matches the username grammar ({@code
 * [a-z0-9][a-z0-9_]{2,15}}) and the lookbehind keeps it from firing inside an email ({@code
 * foo@bar} — the {@code @} is preceded by a word char). Returns distinct handles in first- seen
 * order, capped so a comment stuffed with mentions can't fan out without bound.
 */
final class MentionParser {

  static final int MAX_MENTIONS = 10;

  private static final Pattern MENTION =
      Pattern.compile("(?<![A-Za-z0-9_])@([a-z0-9][a-z0-9_]{2,15})");

  private MentionParser() {}

  static List<String> parse(String body) {
    if (body == null || body.isBlank()) {
      return List.of();
    }
    Set<String> handles = new LinkedHashSet<>();
    Matcher m = MENTION.matcher(body);
    while (m.find() && handles.size() < MAX_MENTIONS) {
      handles.add(m.group(1));
    }
    return List.copyOf(handles);
  }
}
