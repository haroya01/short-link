package com.example.short_link.common.note;

import com.example.short_link.common.user.Usernames;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Members' handles follow the username rule (3–16 of a-z, 0-9, _). A handle followed by another @
// names an account on another server and is not a member. A link is read before handles, so the @
// in a link's path names no one. The web and iOS clients link the same tokens; keep the three in
// step.
public final class Mentions {

  public static final int MAX_MENTIONS = 10;

  public static final String LOCAL_HANDLE =
      "(?<![A-Za-z0-9_])@(" + Usernames.GRAMMAR + ")(?![A-Za-z0-9_@])";

  private static final Pattern LOCAL = Pattern.compile(LOCAL_HANDLE, Pattern.CASE_INSENSITIVE);

  private static final Pattern REMOTE =
      Pattern.compile(
          "(?<![A-Za-z0-9_@])@([A-Za-z0-9_][A-Za-z0-9_.-]{0,63})@([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+)"
              + "(?![A-Za-z0-9_-])");

  private static final Pattern LINK = Pattern.compile("https?://[^\\s<]+");

  private Mentions() {}

  public static String outsideLinks(String text) {
    return LINK.matcher(text).replaceAll(" ");
  }

  // Accounts elsewhere, as user@server in lower case. A trailing full stop ends the sentence, not
  // the server name.
  public static List<String> remote(String text) {
    if (text == null || text.indexOf('@') < 0) {
      return List.of();
    }
    Set<String> handles = new LinkedHashSet<>();
    Matcher matcher = REMOTE.matcher(outsideLinks(text));
    while (matcher.find() && handles.size() < MAX_MENTIONS) {
      String user = matcher.group(1);
      if (user.endsWith(".")) {
        continue;
      }
      handles.add((user + "@" + matcher.group(2)).toLowerCase(Locale.ROOT));
    }
    return List.copyOf(handles);
  }

  public static List<String> of(String text) {
    if (text == null || text.indexOf('@') < 0) {
      return List.of();
    }
    Set<String> handles = new LinkedHashSet<>();
    Matcher matcher = LOCAL.matcher(outsideLinks(text));
    while (matcher.find() && handles.size() < MAX_MENTIONS) {
      handles.add(matcher.group(1).toLowerCase(Locale.ROOT));
    }
    return List.copyOf(handles);
  }
}
