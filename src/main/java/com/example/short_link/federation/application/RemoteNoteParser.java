package com.example.short_link.federation.application;

import static com.example.short_link.federation.application.ActivityStreams.idOf;
import static com.example.short_link.federation.application.ActivityStreams.text;

import com.example.short_link.common.note.RemoteNotes;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;

// Reads a Note from another server the way Mastodon addresses it: Public in "to" is public, in "cc"
// unlisted, a followers collection is followers-only, and anything else is for the people named.
// Content is HTML; it becomes plain text with paragraphs as blank lines. A mention of someone
// elsewhere keeps its server (@bob@host) so it never links to a member who shares the name.
@Component
@RequiredArgsConstructor
public class RemoteNoteParser {

  private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}");

  public static final int MAX_BODY = 5000;

  private static final Set<String> PUBLIC = Set.of(ActivityStreams.PUBLIC, "as:Public", "Public");
  private static final Set<String> BLOCKS =
      Set.of("p", "div", "blockquote", "li", "pre", "h1", "h2", "h3", "h4", "h5", "h6");
  private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");
  private static final Pattern ATTRIBUTE =
      Pattern.compile("([a-zA-Z-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))");

  private final FederationUrls urls;

  public record Parsed(
      String uri,
      String url,
      String body,
      String contentWarning,
      boolean sensitive,
      String visibility,
      Instant publishedAt,
      Optional<Long> inReplyToLocalId,
      String inReplyToUri,
      Set<String> addressedPublicIds,
      List<RemoteNotes.Media> media,
      String language) {}

  public Parsed parse(JsonNode object, JsonNode activity) {
    String inReplyTo = idOf(object.get("inReplyTo"));
    Set<String> to = addresses(object.get("to"), activity.get("to"));
    Set<String> cc = addresses(object.get("cc"), activity.get("cc"));
    Set<String> addressed = new LinkedHashSet<>();
    for (String address : to) {
      urls.publicIdOf(address).ifPresent(addressed::add);
    }
    for (String address : cc) {
      urls.publicIdOf(address).ifPresent(addressed::add);
    }
    JsonNode tags = object.get("tag");
    if (tags != null && tags.isArray()) {
      for (JsonNode tag : tags) {
        if ("Mention".equals(text(tag.get("type")))) {
          urls.publicIdOf(text(tag.get("href"))).ifPresent(addressed::add);
        }
      }
    }
    return new Parsed(
        idOf(object),
        link(object.get("url")),
        body(object),
        warning(object.get("summary")),
        object.path("sensitive").asBoolean(false),
        visibility(to, cc),
        published(object.get("published")),
        urls.noteIdOf(inReplyTo),
        inReplyTo,
        addressed,
        media(object.get("attachment")),
        language(object.get("contentMap")));
  }

  // Mastodon names a note's language as the one key of contentMap; a region tag is dropped.
  private static String language(JsonNode contentMap) {
    if (contentMap == null || !contentMap.isObject() || contentMap.isEmpty()) {
      return null;
    }
    String key = contentMap.propertyNames().iterator().next();
    String code = key.split("[-_]")[0].toLowerCase(Locale.ROOT);
    return LANGUAGE.matcher(code).matches() ? code : null;
  }

  public boolean addressesUs(JsonNode object, JsonNode activity) {
    for (String address : addresses(object.get("to"), activity.get("to"))) {
      if (urls.publicIdOf(address).isPresent()) {
        return true;
      }
    }
    for (String address : addresses(object.get("cc"), activity.get("cc"))) {
      if (urls.publicIdOf(address).isPresent()) {
        return true;
      }
    }
    JsonNode tags = object.get("tag");
    if (tags != null && tags.isArray()) {
      for (JsonNode tag : tags) {
        if ("Mention".equals(text(tag.get("type")))
            && urls.publicIdOf(text(tag.get("href"))).isPresent()) {
          return true;
        }
      }
    }
    return false;
  }

  static String visibility(Set<String> to, Set<String> cc) {
    if (to.stream().anyMatch(PUBLIC::contains)) {
      return "public";
    }
    if (cc.stream().anyMatch(PUBLIC::contains)) {
      return "unlisted";
    }
    boolean followers =
        to.stream().anyMatch(address -> address.endsWith("/followers"))
            || cc.stream().anyMatch(address -> address.endsWith("/followers"));
    return followers ? "private" : "direct";
  }

  String body(JsonNode object) {
    String html = text(object.get("content"));
    if (html == null) {
      JsonNode map = object.get("contentMap");
      if (map != null && map.isObject()) {
        for (JsonNode value : map) {
          html = text(value);
          if (html != null) {
            break;
          }
        }
      }
    }
    if (html == null) {
      return "";
    }
    String flat = plain(html).replace('\u00a0', ' ').replaceAll("[ \\t]+\\n", "\n");
    flat = flat.replaceAll("\\n{3,}", "\n\n").strip();
    return flat.codePointCount(0, flat.length()) <= MAX_BODY
        ? flat
        : flat.substring(0, flat.offsetByCodePoints(0, MAX_BODY - 1)) + "…";
  }

  // Mastodon sanitizes content to a few tags (p, br, a, span and simple formatting), so a tag scan
  // is enough; anything else is dropped and its text kept.
  private String plain(String html) {
    StringBuilder out = new StringBuilder();
    Matcher tag = TAG.matcher(html);
    int at = 0;
    String href = null;
    String classes = null;
    StringBuilder label = null;
    while (tag.find()) {
      (label != null ? label : out).append(HtmlUtils.htmlUnescape(html.substring(at, tag.start())));
      at = tag.end();
      boolean closing = !tag.group(1).isEmpty();
      String name = tag.group(2).toLowerCase(Locale.ROOT);
      if (name.equals("br")) {
        (label != null ? label : out).append('\n');
      } else if (name.equals("a") && !closing && label == null) {
        href = attribute(tag.group(3), "href");
        classes = attribute(tag.group(3), "class");
        label = new StringBuilder();
      } else if (name.equals("a") && closing && label != null) {
        out.append(anchor(label.toString().strip(), href, classes));
        label = null;
      } else if (BLOCKS.contains(name) && label == null) {
        paragraphBreak(out);
      }
    }
    (label != null ? label : out).append(HtmlUtils.htmlUnescape(html.substring(at)));
    if (label != null) {
      out.append(anchor(label.toString().strip(), href, classes));
    }
    return out.toString();
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

  private static void paragraphBreak(StringBuilder out) {
    if (out.isEmpty()) {
      return;
    }
    int trailing = 0;
    for (int i = out.length() - 1; i >= 0 && out.charAt(i) == '\n'; i--) {
      trailing++;
    }
    out.append("\n".repeat(Math.max(0, 2 - trailing)));
  }

  private String anchor(String label, String href, String classes) {
    Set<String> kinds = Set.of(classes == null ? new String[0] : classes.split("\\s+"));
    if (kinds.contains("mention") && !kinds.contains("hashtag") && label.startsWith("@")) {
      if (label.indexOf('@', 1) > 0 || href.isEmpty() || urls.isOurs(href)) {
        return label;
      }
      String host = RemoteActorParser.host(href);
      return host == null ? label : label + "@" + host;
    }
    if (kinds.contains("hashtag") || label.startsWith("#")) {
      return label;
    }
    if (href.isEmpty()) {
      return label;
    }
    String bare = label.replace("…", "").replaceFirst("^https?://", "");
    return bare.isEmpty() || href.contains(bare) ? href : label + " (" + href + ")";
  }

  private static String warning(JsonNode summary) {
    String raw = text(summary);
    if (raw == null) {
      return null;
    }
    String plain = HtmlUtils.htmlUnescape(TAG.matcher(raw).replaceAll(" ")).strip();
    plain = plain.replaceAll("\\s+", " ");
    return plain.isEmpty() ? null : plain;
  }

  public static Instant updated(JsonNode object) {
    return published(object.get("updated"));
  }

  private static Instant published(JsonNode node) {
    String raw = text(node);
    if (raw == null) {
      return null;
    }
    try {
      return Instant.parse(raw);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static List<RemoteNotes.Media> media(JsonNode attachments) {
    List<RemoteNotes.Media> media = new ArrayList<>();
    if (attachments == null) {
      return media;
    }
    Iterable<JsonNode> items = attachments.isArray() ? attachments : List.of(attachments);
    for (JsonNode attachment : items) {
      String type = mediaType(attachment);
      String url = link(attachment.get("url"));
      if (type != null && url != null && url.startsWith("https://") && url.length() <= 512) {
        media.add(
            new RemoteNotes.Media(
                url,
                text(attachment.get("name")),
                type,
                side(attachment.get("width")),
                side(attachment.get("height"))));
      }
    }
    return media;
  }

  private static Integer side(JsonNode node) {
    return node != null && node.canConvertToInt() && node.isIntegralNumber()
        ? node.intValue()
        : null;
  }

  // Pictures, video (Mastodon's GIFs arrive as silent mp4) and audio stay on their server and play
  // from there; anything else is left out.
  private static String mediaType(JsonNode attachment) {
    String declared = text(attachment.get("mediaType"));
    if (declared != null) {
      String type = declared.toLowerCase(Locale.ROOT);
      return type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/")
          ? type
          : null;
    }
    return switch (String.valueOf(text(attachment.get("type")))) {
      case "Image" -> "image/jpeg";
      case "Video" -> "video/mp4";
      case "Audio" -> "audio/mpeg";
      default -> null;
    };
  }

  private static Set<String> addresses(JsonNode primary, JsonNode fallback) {
    JsonNode node = primary != null ? primary : fallback;
    Set<String> addresses = new LinkedHashSet<>();
    if (node == null) {
      return addresses;
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        String id = idOf(item);
        if (id != null) {
          addresses.add(id);
        }
      }
    } else {
      String id = idOf(node);
      if (id != null) {
        addresses.add(id);
      }
    }
    return addresses;
  }

  private static String link(JsonNode node) {
    if (node == null) {
      return null;
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        String href = link(item);
        if (href != null) {
          return href;
        }
      }
      return null;
    }
    if (node.isObject()) {
      String href = text(node.get("href"));
      return href != null ? href : text(node.get("url"));
    }
    return text(node);
  }
}
