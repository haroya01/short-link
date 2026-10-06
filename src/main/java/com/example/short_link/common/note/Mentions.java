package com.example.short_link.common.note;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Members' handles follow the username rule (3–16 of a-z, 0-9, _). A handle followed by another @
// names an account on another server and is not a member. The web and iOS clients link the same
// tokens; keep the three in step.
public final class Mentions {

  public static final int MAX_MENTIONS = 10;

  private static final Pattern LOCAL =
      Pattern.compile(
          "(?<![A-Za-z0-9_])@([a-z0-9][a-z0-9_]{2,15})(?![A-Za-z0-9_@])", Pattern.CASE_INSENSITIVE);

  private Mentions() {}

  public static List<String> of(String text) {
    if (text == null || text.indexOf('@') < 0) {
      return List.of();
    }
    Set<String> handles = new LinkedHashSet<>();
    Matcher matcher = LOCAL.matcher(text);
    while (matcher.find() && handles.size() < MAX_MENTIONS) {
      handles.add(matcher.group(1).toLowerCase(Locale.ROOT));
    }
    return List.copyOf(handles);
  }
}
