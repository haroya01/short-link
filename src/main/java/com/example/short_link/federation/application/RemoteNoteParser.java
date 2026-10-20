package com.example.short_link.federation.application;

import static com.example.short_link.federation.application.ActivityStreams.idOf;
import static com.example.short_link.federation.application.ActivityStreams.text;

import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.common.note.RemoteNotes.Kind;
import com.example.short_link.common.note.RemoteNotes.Shape;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
// A Question is a note with a poll. An Article, Page or Video is kept as a card, as Mastodon
// converts them: its title, its summary or the start of its text, one picture and its address;
// the full text and the video stay on their server.
@Component
@RequiredArgsConstructor
public class RemoteNoteParser {

  private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}");
  private static final Pattern COMMUNITY_PATH = Pattern.compile("/[cm]/[^/]+/?");
  private static final Pattern COMMUNITY_NAME = Pattern.compile("[\\p{L}\\p{N}_.-]{1,100}");

  public static final int MAX_BODY = 5000;
  public static final int MAX_EXCERPT = 500;

  // A poll fits the note's poll column as a member's does: 2 to 4 options of up to 50 characters.
  private static final int MAX_POLL_OPTIONS = 4;
  private static final int MAX_POLL_OPTION = 50;

  private static final Map<String, Kind> KINDS =
      Map.of(
          "Note", Kind.NOTE,
          "Question", Kind.QUESTION,
          "Article", Kind.ARTICLE,
          "Page", Kind.PAGE,
          "Video", Kind.VIDEO);

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
      NoteVisibility visibility,
      Instant publishedAt,
      Optional<Long> inReplyToLocalId,
      String inReplyToUri,
      Set<String> addressedPublicIds,
      List<RemoteNotes.Media> media,
      String language,
      Shape shape) {}

  public static Optional<Kind> kindOf(JsonNode object) {
    String type = object == null ? null : text(object.get("type"));
    return type == null ? Optional.empty() : Optional.ofNullable(KINDS.get(type));
  }

  public Parsed parse(JsonNode object, JsonNode activity) {
    Kind kind = kindOf(object).orElse(Kind.NOTE);
    boolean card = kind == Kind.ARTICLE || kind == Kind.PAGE || kind == Kind.VIDEO;
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
    String uri = idOf(object);
    return new Parsed(
        uri,
        address(object.get("url"), uri),
        card ? excerpt(object) : body(object),
        card ? null : oneLine(object.get("summary")),
        object.path("sensitive").asBoolean(false),
        visibility(to, cc),
        published(object.get("published")),
        urls.noteIdOf(inReplyTo),
        inReplyTo,
        addressed,
        card ? cover(object, kind) : media(object.get("attachment")),
        language(object.get("contentMap")),
        new Shape(
            kind,
            card ? oneLine(object.get("name")) : null,
            community(object, kind),
            kind == Kind.QUESTION ? poll(object) : null));
  }

  // The page people open: an http(s) address on the object's own server, or none.
  private static String address(JsonNode url, String uri) {
    String href = webLink(url);
    String host = uri == null ? null : RemoteActorParser.host(uri);
    return href != null && host != null && host.equals(RemoteActorParser.host(href)) ? href : null;
  }

  // PeerTube lists the watch page beside the video files; the text/html link is the page.
  private static String webLink(JsonNode url) {
    if (url != null && url.isArray()) {
      for (JsonNode item : url) {
        if (String.valueOf(text(item.get("mediaType"))).startsWith("text/html")) {
          return text(item.get("href"));
        }
      }
    }
    return link(url);
  }

  // Lemmy names the community a post went to in audience, and older servers only in to.
  private static String community(JsonNode object, Kind kind) {
    String group = idOf(object.get("audience"));
    if (group == null && kind == Kind.PAGE) {
      for (String address : addresses(object.get("to"), null)) {
        String path = path(address);
        if (path != null && COMMUNITY_PATH.matcher(path).matches()) {
          group = address;
          break;
        }
      }
    }
    String host = group == null ? null : RemoteActorParser.host(group);
    String path = host == null ? null : path(group);
    if (path == null) {
      return null;
    }
    String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    String name = trimmed.substring(trimmed.lastIndexOf('/') + 1);
    return COMMUNITY_NAME.matcher(name).matches() ? name + "@" + host : null;
  }

  private static String path(String address) {
    try {
      return URI.create(address).getPath();
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }

  static RemoteNotes.Poll poll(JsonNode question) {
    JsonNode single = question.get("oneOf");
    boolean multiple = single == null || !single.isArray();
    JsonNode choices = multiple ? question.get("anyOf") : single;
    if (choices == null
        || !choices.isArray()
        || choices.size() < 2
        || choices.size() > MAX_POLL_OPTIONS) {
      return null;
    }
    List<String> options = new ArrayList<>();
    List<Long> votes = new ArrayList<>();
    long total = 0;
    for (JsonNode choice : choices) {
      String name = oneLine(choice.get("name"));
      if (name == null) {
        return null;
      }
      long count = count(choice.path("replies").get("totalItems"));
      options.add(cut(name, MAX_POLL_OPTION));
      votes.add(count);
      total += count;
    }
    JsonNode voters = question.get("votersCount");
    Instant ends = published(question.get("endTime"));
    JsonNode closedNode = question.get("closed");
    Instant closedAt = published(closedNode);
    return new RemoteNotes.Poll(
        List.copyOf(options),
        List.copyOf(votes),
        voters == null ? total : count(voters),
        multiple,
        closedAt != null && (ends == null || closedAt.isBefore(ends)) ? closedAt : ends,
        closedAt != null || (closedNode != null && closedNode.asBoolean(false)));
  }

  private static long count(JsonNode node) {
    return node != null && node.isIntegralNumber() && node.longValue() > 0 ? node.longValue() : 0;
  }

  private String excerpt(JsonNode object) {
    String summary = html(object, "summary", "summaryMap");
    return cut(
        flatten(summary != null ? summary : html(object, "content", "contentMap")), MAX_EXCERPT);
  }

  // PeerTube sends a small thumbnail and a wider preview as a video's icons.
  private static List<RemoteNotes.Media> cover(JsonNode object, Kind kind) {
    List<RemoteNotes.Media> pictures = new ArrayList<>();
    if (kind == Kind.VIDEO) {
      pictures.addAll(pictures(object.get("icon")));
      pictures.sort(
          Comparator.comparing(
                  (RemoteNotes.Media picture) -> picture.width() == null ? 0 : picture.width())
              .reversed());
    }
    pictures.addAll(pictures(object.get("image")));
    if (kind != Kind.VIDEO) {
      pictures.addAll(pictures(object.get("attachment")));
    }
    return pictures.isEmpty() ? List.of() : List.of(pictures.getFirst());
  }

  private static List<RemoteNotes.Media> pictures(JsonNode node) {
    String bare = text(node);
    if (bare != null) {
      return bare.startsWith("https://") && bare.length() <= 512
          ? List.of(new RemoteNotes.Media(bare, null, "image/jpeg"))
          : List.of();
    }
    return media(node).stream().filter(media -> media.contentType().startsWith("image/")).toList();
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

  static NoteVisibility visibility(Set<String> to, Set<String> cc) {
    return NoteVisibility.addressed(
        to.stream().anyMatch(PUBLIC::contains),
        cc.stream().anyMatch(PUBLIC::contains),
        to.stream().anyMatch(address -> address.endsWith("/followers"))
            || cc.stream().anyMatch(address -> address.endsWith("/followers")));
  }

  String body(JsonNode object) {
    return cut(flatten(html(object, "content", "contentMap")), MAX_BODY);
  }

  private static String html(JsonNode object, String field, String mapField) {
    String html = text(object.get(field));
    if (html == null) {
      JsonNode map = object.get(mapField);
      if (map != null && map.isObject()) {
        for (JsonNode value : map) {
          html = text(value);
          if (html != null) {
            break;
          }
        }
      }
    }
    return html;
  }

  private static String cut(String text, int max) {
    return text.codePointCount(0, text.length()) <= max
        ? text
        : text.substring(0, text.offsetByCodePoints(0, max - 1)) + "…";
  }

  private String flatten(String html) {
    if (html == null) {
      return "";
    }
    String flat = plain(html).replace('\u00a0', ' ').replaceAll("[ \\t]+\\n", "\n");
    return flat.replaceAll("\\n{3,}", "\n\n").strip();
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

  private static String oneLine(JsonNode node) {
    String raw = text(node);
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
