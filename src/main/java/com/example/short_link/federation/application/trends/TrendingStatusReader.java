package com.example.short_link.federation.application.trends;

import static com.example.short_link.federation.application.ActivityStreams.text;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.ActivityStreams;
import com.example.short_link.federation.application.RemoteActorParser;
import com.example.short_link.federation.application.RemoteNoteParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// A status from Mastodon's REST API is rewritten as the ActivityPub object its server would
// publish and read by RemoteNoteParser, so its text, warning, media and poll come out exactly as a
// kept remote note's do. Only a public post that is not a reply or a boost, by an account that
// lets itself be found and indexed and whose profile does not say #nobot, is taken.
@Component
@RequiredArgsConstructor
class TrendingStatusReader {

  private static final int MAX_URI = 512;
  private static final int MAX_NAME = 100;
  private static final int MAX_CARD_TITLE = 300;
  private static final int MAX_CARD_DESCRIPTION = 500;
  private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{1,128}");
  private static final Pattern NO_BOT = Pattern.compile("#nobot\\b", Pattern.CASE_INSENSITIVE);

  private final RemoteNoteParser parser;
  private final JsonMapper json;

  record Read(TrendingNote note, String actorUri, String domain, JsonNode object) {}

  Optional<Read> read(JsonNode status, String server) {
    String uri = text(status.get("uri"));
    JsonNode account = status.path("account");
    String actorUri = text(account.get("uri"));
    String domain = uri == null || uri.length() > MAX_URI ? null : RemoteActorParser.host(uri);
    if (domain == null
        || actorUri == null
        || !domain.equals(RemoteActorParser.host(actorUri))
        || !"public".equals(text(status.get("visibility")))
        || status.hasNonNull("reblog")
        || status.hasNonNull("in_reply_to_id")
        || !account.path("discoverable").asBoolean(false)
        || account.path("noindex").asBoolean(false)
        || NO_BOT.matcher(account.path("note").asString("")).find()) {
      return Optional.empty();
    }
    String username = username(text(account.get("acct")), server);
    if (username == null) {
      return Optional.empty();
    }
    ObjectNode object = object(status, uri);
    object.put("attributedTo", actorUri);
    RemoteNoteParser.Parsed parsed = parser.parse(object, object);
    if (parsed.publishedAt() == null) {
      return Optional.empty();
    }
    TrendingNote note =
        new TrendingNote(
            parsed.body(),
            parsed.publishedAt(),
            RemoteNoteParser.updated(object),
            count(status.get("favourites_count")),
            count(status.get("reblogs_count")),
            count(status.get("replies_count")),
            new TrendingNote.Author(
                null,
                username,
                https(text(account.get("avatar"))),
                cut(text(account.get("display_name")), MAX_NAME),
                null,
                https(text(account.get("url")))),
            parsed.media(),
            card(status.get("card")),
            parsed.contentWarning(),
            parsed.sensitive(),
            parsed.language(),
            poll(parsed.shape().poll()),
            new TrendingNote.Remote(
                parsed.shape().kind().name(),
                parsed.shape().title(),
                parsed.url(),
                parsed.shape().community()),
            uri,
            parsed.url() != null ? parsed.url() : uri,
            List.of(server));
    return Optional.of(new Read(note, actorUri, domain, object));
  }

  private ObjectNode object(JsonNode status, String uri) {
    JsonNode poll = status.get("poll");
    boolean question = poll != null && poll.isObject();
    ObjectNode object = json.createObjectNode();
    object.put("type", question ? "Question" : "Note");
    object.put("id", uri);
    object.set("url", status.get("url"));
    object.set("published", status.get("created_at"));
    object.set("updated", status.get("edited_at"));
    object.put("sensitive", status.path("sensitive").asBoolean(false));
    object.set("summary", status.get("spoiler_text"));
    object.set("content", status.get("content"));
    String language = text(status.get("language"));
    if (language != null) {
      object.putObject("contentMap").set(language, status.get("content"));
    }
    object.putArray("to").add(ActivityStreams.PUBLIC);
    ArrayNode attachments = object.putArray("attachment");
    for (JsonNode media : status.path("media_attachments")) {
      ObjectNode attachment = attachments.addObject();
      attachment.put("type", mediaType(text(media.get("type"))));
      attachment.set("url", media.get("url"));
      attachment.set("name", media.get("description"));
      attachment.set("width", media.path("meta").path("original").get("width"));
      attachment.set("height", media.path("meta").path("original").get("height"));
    }
    if (question) {
      ArrayNode choices =
          object.putArray(poll.path("multiple").asBoolean(false) ? "anyOf" : "oneOf");
      for (JsonNode option : poll.path("options")) {
        ObjectNode choice = choices.addObject();
        choice.set("name", option.get("title"));
        choice.putObject("replies").set("totalItems", option.get("votes_count"));
      }
      if (poll.hasNonNull("voters_count")) {
        object.set("votersCount", poll.get("voters_count"));
      }
      object.set("endTime", poll.get("expires_at"));
      if (poll.path("expired").asBoolean(false)) {
        JsonNode endsAt = poll.get("expires_at");
        if (text(endsAt) != null) {
          object.set("closed", endsAt);
        } else {
          object.put("closed", true);
        }
      }
    }
    return object;
  }

  private static String mediaType(String type) {
    return switch (String.valueOf(type)) {
      case "image" -> "Image";
      case "gifv", "video" -> "Video";
      case "audio" -> "Audio";
      default -> "Document";
    };
  }

  // A local account's acct is its bare name on the server that answered.
  private static String username(String acct, String server) {
    if (acct == null) {
      return null;
    }
    int at = acct.indexOf('@');
    String name = at < 0 ? acct : acct.substring(0, at);
    String host = at < 0 ? server : acct.substring(at + 1).toLowerCase(Locale.ROOT);
    return USERNAME.matcher(name).matches() && RemoteActorParser.host("https://" + host) != null
        ? name + "@" + host
        : null;
  }

  private static TrendingNote.Poll poll(RemoteNotes.Poll poll) {
    if (poll == null) {
      return null;
    }
    List<TrendingNote.PollOption> options = new ArrayList<>();
    long votes = 0;
    for (int i = 0; i < poll.options().size(); i++) {
      options.add(new TrendingNote.PollOption(poll.options().get(i), poll.votes().get(i)));
      votes += poll.votes().get(i);
    }
    return new TrendingNote.Poll(
        poll.endsAt(),
        poll.closed(),
        poll.multiple(),
        votes,
        poll.voters(),
        List.copyOf(options),
        null,
        null);
  }

  private static TrendingNote.LinkPreview card(JsonNode card) {
    String url = card == null ? null : https(text(card.get("url")));
    if (url == null) {
      return null;
    }
    return new TrendingNote.LinkPreview(
        url,
        cut(text(card.get("title")), MAX_CARD_TITLE),
        cut(text(card.get("description")), MAX_CARD_DESCRIPTION),
        https(text(card.get("image"))));
  }

  private static String https(String url) {
    return url != null && url.startsWith("https://") && url.length() <= MAX_URI ? url : null;
  }

  private static String cut(String text, int max) {
    if (text == null) {
      return null;
    }
    String line = text.replaceAll("\\s+", " ").strip();
    if (line.isEmpty()) {
      return null;
    }
    return line.codePointCount(0, line.length()) <= max
        ? line
        : line.substring(0, line.offsetByCodePoints(0, max - 1)) + "…";
  }

  private static long count(JsonNode node) {
    return node != null && node.isIntegralNumber() && node.longValue() > 0 ? node.longValue() : 0;
  }
}
