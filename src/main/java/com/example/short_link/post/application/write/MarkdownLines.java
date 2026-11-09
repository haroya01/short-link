package com.example.short_link.post.application.write;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownLines {

  // Mirrors the web editor's unescapeTitle for escaped quotes and backslashes.
  private static final Pattern TITLE_ESCAPE = Pattern.compile("\\\\([\"\\\\])");
  private static final Pattern BARE_URL = Pattern.compile("^(https?://\\S+)$");
  private static final Pattern IMAGE_EXT =
      Pattern.compile("\\.(?:jpe?g|png|gif|webp)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern TABLE_SEP = Pattern.compile("^[\\s|:-]+$");

  private MarkdownLines() {}

  static boolean isTableStart(String line, String next) {
    if (!line.stripLeading().startsWith("|") || next == null) return false;
    String t = next.trim();
    return TABLE_SEP.matcher(t).matches() && t.contains("-") && t.contains("|");
  }

  // Mirrors the web editor's markdown-to-blocks: only a bare HTTP(S) URL alone on its line
  // stands as its own block. `<url>` and `[text](url)` are links, and a line ending in a
  // backslash breaks inside its paragraph, so both stay in the paragraph.
  static String standaloneUrl(String line) {
    String t = line.trim();
    if (t.endsWith("\\")) return null;
    Matcher m = BARE_URL.matcher(t);
    if (!m.matches()) return null;
    String url = m.group(1);
    try {
      return new URI(url).getHost() == null ? null : url;
    } catch (Exception e) {
      return null;
    }
  }

  static String standaloneImageUrl(String line) {
    String url = standaloneUrl(line);
    if (url == null) return null;
    String path = URI.create(url).getPath();
    return path != null && IMAGE_EXT.matcher(path).find() ? url : null;
  }

  static String unescapeTitle(String s) {
    return TITLE_ESCAPE.matcher(s).replaceAll("$1");
  }
}
