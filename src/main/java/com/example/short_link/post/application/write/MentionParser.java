package com.example.short_link.post.application.write;

import com.example.short_link.common.note.Mentions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ignores handles inside email addresses and links, and a handle followed by another @ (an account
// on another server; comments do not federate, so it names no one). Returns distinct handles in
// first-seen order, capped to prevent unbounded notification fan-out.
public final class MentionParser {

  static final int MAX_MENTIONS = Mentions.MAX_MENTIONS;

  private static final Pattern MENTION = Pattern.compile(Mentions.LOCAL_HANDLE);

  private MentionParser() {}

  public static List<String> parse(String body) {
    if (body == null || body.isBlank()) {
      return List.of();
    }
    Set<String> handles = new LinkedHashSet<>();
    Matcher m = MENTION.matcher(Mentions.outsideLinks(body));
    while (m.find() && handles.size() < MAX_MENTIONS) {
      handles.add(m.group(1));
    }
    return List.copyOf(handles);
  }
}
