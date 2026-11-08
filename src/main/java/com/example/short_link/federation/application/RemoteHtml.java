package com.example.short_link.federation.application;

import static com.example.short_link.federation.application.ActivityStreams.text;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;

// Mastodon sanitizes content to a few tags (p, br, a, span and simple formatting), so a tag scan
// is enough; anything else is dropped and its text kept.
final class RemoteHtml {

  private static final Set<String> BLOCKS =
      Set.of("p", "div", "blockquote", "li", "pre", "h1", "h2", "h3", "h4", "h5", "h6");
  private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");
  private static final Pattern ATTRIBUTE =
      Pattern.compile("([a-zA-Z-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))");

  private final FederationUrls urls;

  RemoteHtml(FederationUrls urls) {
    this.urls = urls;
  }

  static String html(JsonNode object, String field, String mapField) {
    String html = text(object.get(field));
    if (html != null) {
      return html;
    }
    JsonNode map = object.get(mapField);
    if (map == null || !map.isObject()) {
      return null;
    }
    for (JsonNode value : map) {
      String translated = text(value);
      if (translated != null) {
        return translated;
      }
    }
    return null;
  }

  static String cut(String text, int max) {
    return text.codePointCount(0, text.length()) <= max
        ? text
        : text.substring(0, text.offsetByCodePoints(0, max - 1)) + "…";
  }

  static String oneLine(JsonNode node) {
    String raw = text(node);
    if (raw == null) {
      return null;
    }
    String plain = HtmlUtils.htmlUnescape(TAG.matcher(raw).replaceAll(" ")).strip();
    plain = plain.replaceAll("\\s+", " ");
    return plain.isEmpty() ? null : plain;
  }

  String flatten(String html) {
    if (html == null) {
      return "";
    }
    String flat = plain(html).replace('\u00a0', ' ').replaceAll("[ \\t]+\\n", "\n");
    return flat.replaceAll("\\n{3,}", "\n\n").strip();
  }

  private String plain(String html) {
    PlainText text = new PlainText();
    Matcher tag = TAG.matcher(html);
    int at = 0;
    while (tag.find()) {
      text.append(HtmlUtils.htmlUnescape(html.substring(at, tag.start())));
      at = tag.end();
      text.tag(tag.group(2).toLowerCase(Locale.ROOT), !tag.group(1).isEmpty(), tag.group(3));
    }
    text.append(HtmlUtils.htmlUnescape(html.substring(at)));
    return text.finish();
  }

  private final class PlainText {

    private final StringBuilder out = new StringBuilder();
    private StringBuilder label;
    private String href;
    private String classes;

    void append(String text) {
      (label != null ? label : out).append(text);
    }

    void tag(String name, boolean closing, String attributes) {
      if (name.equals("br")) {
        append("\n");
      } else if (name.equals("a")) {
        anchorTag(closing, attributes);
      } else if (BLOCKS.contains(name) && label == null) {
        paragraphBreak();
      }
    }

    private void anchorTag(boolean closing, String attributes) {
      if (!closing && label == null) {
        href = attribute(attributes, "href");
        classes = attribute(attributes, "class");
        label = new StringBuilder();
      } else if (closing && label != null) {
        closeAnchor();
      }
    }

    private void closeAnchor() {
      out.append(anchor(label.toString().strip(), href, classes));
      label = null;
    }

    private void paragraphBreak() {
      if (out.isEmpty()) {
        return;
      }
      int trailing = 0;
      for (int i = out.length() - 1; i >= 0 && out.charAt(i) == '\n'; i--) {
        trailing++;
      }
      out.append("\n".repeat(Math.max(0, 2 - trailing)));
    }

    String finish() {
      if (label != null) {
        closeAnchor();
      }
      return out.toString();
    }
  }

  private static String attribute(String attributes, String name) {
    Matcher m = ATTRIBUTE.matcher(attributes);
    while (m.find()) {
      if (m.group(1).equalsIgnoreCase(name)) {
        String value =
            m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
        return HtmlUtils.htmlUnescape(value).strip();
      }
    }
    return "";
  }

  private String anchor(String label, String href, String classes) {
    Set<String> kinds = Set.of(classes == null ? new String[0] : classes.split("\\s+"));
    if (kinds.contains("mention") && !kinds.contains("hashtag") && label.startsWith("@")) {
      return mention(label, href);
    }
    if (kinds.contains("hashtag") || label.startsWith("#") || href.isEmpty()) {
      return label;
    }
    return link(label, href);
  }

  private static String link(String label, String href) {
    String bare = label.replace("…", "").replaceFirst("^https?://", "");
    return bare.isEmpty() || href.contains(bare) ? href : label + " (" + href + ")";
  }

  private String mention(String label, String href) {
    if (label.indexOf('@', 1) > 0 || href.isEmpty() || urls.isOurs(href)) {
      return label;
    }
    // A mention elsewhere keeps its server so it never links to a member who shares the name.
    String host = RemoteActorParser.host(href);
    return host == null ? label : label + "@" + host;
  }
}
