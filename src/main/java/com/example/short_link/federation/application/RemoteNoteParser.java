package com.example.short_link.federation.application;

import static com.example.short_link.federation.application.ActivityStreams.MAX_URI;
import static com.example.short_link.federation.application.ActivityStreams.dateTime;
import static com.example.short_link.federation.application.ActivityStreams.idOf;
import static com.example.short_link.federation.application.ActivityStreams.text;

import com.example.short_link.common.federation.CustomEmoji;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.common.note.RemoteNotes.Kind;
import com.example.short_link.common.note.RemoteNotes.Shape;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;

@Component
public class RemoteNoteParser {

  private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}");
  private static final Pattern COMMUNITY_PATH = Pattern.compile("/[cm]/[^/]+/?");
  private static final Pattern COMMUNITY_NAME = Pattern.compile("[\\p{L}\\p{N}_.-]{1,100}");

  public static final int MAX_BODY = 5000;
  public static final int MAX_EXCERPT = 500;

  private static final Map<String, Kind> KINDS =
      Map.of(
          "Note", Kind.NOTE,
          "Question", Kind.QUESTION,
          "Article", Kind.ARTICLE,
          "Page", Kind.PAGE,
          "Video", Kind.VIDEO);

  private static final Set<String> PUBLIC = Set.of(ActivityStreams.PUBLIC, "as:Public", "Public");
  private static final Pattern QUOTE_INLINE =
      Pattern.compile(
          "<(p|span|div)\\b[^>]*\\bclass\\s*=\\s*[\"'][^\"']*\\bquote-inline\\b[^\"']*[\"'][^>]*>(.*?)</\\1\\s*>",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern HREF =
      Pattern.compile("<a\\b[^>]*\\bhref\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
  private static final List<String> QUOTE_FIELDS =
      List.of("quote", "quoteUrl", "quoteUri", "_misskey_quote");

  private final FederationUrls urls;
  private final RemoteHtml html;

  public RemoteNoteParser(FederationUrls urls) {
    this.urls = urls;
    this.html = new RemoteHtml(urls);
  }

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
      Shape shape,
      Quote quote,
      Long quotedNoteId,
      List<CustomEmoji> emojis) {

    public Parsed quoting(String body, Long quotedNoteId) {
      return new Parsed(
          uri,
          url,
          body,
          contentWarning,
          sensitive,
          visibility,
          publishedAt,
          inReplyToLocalId,
          inReplyToUri,
          addressedPublicIds,
          media,
          language,
          shape,
          null,
          quotedNoteId,
          emojis);
    }

    public String linkedBody() {
      if (quote == null) {
        return body;
      }
      return body.isEmpty() ? quote.link() : body + "\n\n" + quote.link();
    }

    public Parsed acceptingQuote() {
      return quote == null
          ? this
          : new Parsed(
              uri,
              url,
              body,
              contentWarning,
              sensitive,
              visibility,
              publishedAt,
              inReplyToLocalId,
              inReplyToUri,
              addressedPublicIds,
              media,
              language,
              shape,
              new Quote(quote.uri(), quote.link(), quote.authorization(), true),
              null,
              emojis);
    }
  }

  // The post this one quotes: its id, the address to show when it cannot be shown inline, and the
  // QuoteAuthorization its author's server issued (FEP-044f). accepted is set when the post came
  // through a server's API that already checked the authorization.
  public record Quote(String uri, String link, String authorization, boolean accepted) {}

  public static Optional<Kind> kindOf(JsonNode object) {
    String type = object == null ? null : text(object.get("type"));
    return type == null ? Optional.empty() : Optional.ofNullable(KINDS.get(type));
  }

  public Parsed parse(JsonNode object, JsonNode activity) {
    Kind kind = kindOf(object).orElse(Kind.NOTE);
    boolean card = card(kind);
    String inReplyTo = idOf(object.get("inReplyTo"));
    Set<String> to = addresses(object.get("to"), activity.get("to"));
    Set<String> cc = addresses(object.get("cc"), activity.get("cc"));
    Set<String> addressed = addressedPublicIds(to, cc, object.get("tag"));
    String uri = idOf(object);
    Quote quote = card ? null : quote(object);
    String body = card ? excerpt(object) : body(object);
    if (quote != null) {
      Quoting quoting = quoting(object, quote);
      body = quoting.body();
      quote = new Quote(quote.uri(), quoting.link(), quote.authorization(), false);
    }
    return new Parsed(
        uri,
        RemoteAttachments.address(object.get("url"), uri),
        body,
        card ? null : RemoteHtml.oneLine(object.get("summary")),
        object.path("sensitive").asBoolean(false),
        visibility(to, cc),
        dateTime(object.get("published")),
        urls.noteIdOf(inReplyTo),
        inReplyTo,
        addressed,
        card
            ? RemoteAttachments.cover(object, kind)
            : RemoteAttachments.media(object.get("attachment")),
        language(object.get("contentMap")),
        shape(object, kind, card),
        quote,
        null,
        RemoteEmojis.fromTags(object.get("tag")));
  }

  // An Article, Page or Video is kept as a card, as Mastodon converts them: title, summary or
  // start of the text, one picture, the address; the full text and video stay on their server.
  private static boolean card(Kind kind) {
    return kind == Kind.ARTICLE || kind == Kind.PAGE || kind == Kind.VIDEO;
  }

  private static Shape shape(JsonNode object, Kind kind, boolean card) {
    return new Shape(
        kind,
        card ? RemoteHtml.oneLine(object.get("name")) : null,
        community(object, kind),
        kind == Kind.QUESTION ? RemotePolls.poll(object) : null);
  }

  private static Quote quote(JsonNode object) {
    for (String field : QUOTE_FIELDS) {
      String id = idOf(object.get(field));
      if (id != null && id.length() <= MAX_URI && RemoteActorParser.host(id) != null) {
        String authorization = idOf(object.get("quoteAuthorization"));
        return new Quote(
            id,
            null,
            authorization != null && RemoteActorParser.host(authorization) != null
                ? authorization
                : null,
            false);
      }
    }
    return null;
  }

  // Mastodon starts a quote's text with "RE: <address>" for servers that cannot show quotes; a
  // server that can drops it. Only a first line naming one of the addresses goes.
  public static String withoutReLine(String body, Collection<String> addresses) {
    for (String address : addresses) {
      String line = "RE: " + address;
      if (address != null
          && body.startsWith(line)
          && (body.length() == line.length() || body.charAt(line.length()) == '\n')) {
        return body.substring(line.length()).strip();
      }
    }
    return body;
  }

  // Lemmy names the community a post went to in audience, and older servers only in to.
  private static String community(JsonNode object, Kind kind) {
    String group = idOf(object.get("audience"));
    if (group == null && kind == Kind.PAGE) {
      group = communityAddressed(object.get("to"));
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

  private static String communityAddressed(JsonNode to) {
    for (String address : addresses(to, null)) {
      String path = path(address);
      if (path != null && COMMUNITY_PATH.matcher(path).matches()) {
        return address;
      }
    }
    return null;
  }

  private static String path(String address) {
    try {
      return URI.create(address).getPath();
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }

  private String excerpt(JsonNode object) {
    String summary = RemoteHtml.html(object, "summary", "summaryMap");
    return RemoteHtml.cut(
        html.flatten(summary != null ? summary : RemoteHtml.html(object, "content", "contentMap")),
        MAX_EXCERPT);
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
    return !addressedPublicIds(
            addresses(object.get("to"), activity.get("to")),
            addresses(object.get("cc"), activity.get("cc")),
            object.get("tag"))
        .isEmpty();
  }

  private Set<String> addressedPublicIds(Set<String> to, Set<String> cc, JsonNode tags) {
    Set<String> addressed = new LinkedHashSet<>();
    for (String address : to) {
      urls.publicIdOf(address).ifPresent(addressed::add);
    }
    for (String address : cc) {
      urls.publicIdOf(address).ifPresent(addressed::add);
    }
    if (tags != null && tags.isArray()) {
      for (JsonNode tag : tags) {
        if ("Mention".equals(text(tag.get("type")))) {
          urls.publicIdOf(text(tag.get("href"))).ifPresent(addressed::add);
        }
      }
    }
    return addressed;
  }

  // Mastodon's addressing: Public in "to" is public, in "cc" unlisted; a followers collection is
  // followers-only; anything else is for the people named.
  static NoteVisibility visibility(Set<String> to, Set<String> cc) {
    return NoteVisibility.addressed(
        to.stream().anyMatch(PUBLIC::contains),
        cc.stream().anyMatch(PUBLIC::contains),
        to.stream().anyMatch(address -> address.endsWith("/followers"))
            || cc.stream().anyMatch(address -> address.endsWith("/followers")));
  }

  String body(JsonNode object) {
    return RemoteHtml.cut(html.flatten(RemoteHtml.html(object, "content", "contentMap")), MAX_BODY);
  }

  private record Quoting(String body, String link) {}

  // The quote's own paragraph (class quote-inline) goes, and its link is the one to show; without
  // that paragraph a first line "RE: <the quoted id>" goes instead.
  private Quoting quoting(JsonNode object, Quote quote) {
    String content = RemoteHtml.html(object, "content", "contentMap");
    Matcher inline = content == null ? null : QUOTE_INLINE.matcher(content);
    if (inline == null || !inline.find()) {
      return new Quoting(withoutReLine(body(object), List.of(quote.uri())), quote.uri());
    }
    Matcher href = HREF.matcher(inline.group(2));
    String link = href.find() ? HtmlUtils.htmlUnescape(href.group(1)).strip() : null;
    return new Quoting(
        RemoteHtml.cut(html.flatten(inline.replaceAll("")), MAX_BODY),
        link != null && link.startsWith("https://") && link.length() <= MAX_URI
            ? link
            : quote.uri());
  }

  public static Instant updated(JsonNode object) {
    return dateTime(object.get("updated"));
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
}
