package com.example.short_link.federation.application;

import com.example.short_link.common.note.Hashtags;
import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

// Renders a note as an ActivityStreams Note. Remote servers sanitise HTML themselves, but we only
// ever emit escaped text, <p>, <br> and <a href> so nothing user-written becomes markup.
@Component
@RequiredArgsConstructor
public class NoteDocuments {

  private static final Pattern URL = Pattern.compile("https?://[^\\s<]+");
  private static final Pattern TOKEN =
      Pattern.compile("(" + URL.pattern() + ")|" + Hashtags.PATTERN.pattern());
  private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,!?:;)\\]'\"]+$");

  private final FederationUrls urls;

  public Map<String, Object> note(NoteSnapshot note, String actorPublicId) {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("id", urls.note(note.id()));
    object.put("type", "Note");
    object.put("attributedTo", urls.actor(actorPublicId));
    object.put("content", content(note));
    object.put("published", note.createdAt().truncatedTo(ChronoUnit.SECONDS).toString());
    if (note.editedAt() != null) {
      object.put("updated", note.editedAt().truncatedTo(ChronoUnit.SECONDS).toString());
    }
    object.put("url", urls.notePage(note.authorUsername(), note.id()));
    object.put("to", to(note, actorPublicId));
    object.put("cc", cc(note, actorPublicId));
    object.put("inReplyTo", note.inReplyToId() == null ? null : urls.note(note.inReplyToId()));
    if (note.quotedNote() != null) {
      String quoted = urls.note(note.quotedNote().id());
      object.put("quoteUrl", quoted);
      object.put("quoteUri", quoted);
      object.put("_misskey_quote", quoted);
    }
    object.put("summary", note.contentWarning());
    object.put("sensitive", note.sensitive());
    List<Map<String, Object>> attachments = new ArrayList<>();
    for (var image : note.images()) {
      Map<String, Object> attachment = new LinkedHashMap<>();
      attachment.put("type", "Document");
      attachment.put("mediaType", image.contentType());
      attachment.put("url", image.url());
      attachment.put("name", image.altText());
      attachments.add(attachment);
    }
    object.put("attachment", attachments);
    List<Map<String, Object>> tags = new ArrayList<>();
    for (String name : Hashtags.of(note.body())) {
      Map<String, Object> tag = new LinkedHashMap<>();
      tag.put("type", "Hashtag");
      tag.put("href", urls.tag(name));
      tag.put("name", "#" + name);
      tags.add(tag);
    }
    object.put("tag", tags);
    return object;
  }

  public Map<String, Object> create(NoteSnapshot note, String actorPublicId) {
    String noteUri = urls.note(note.id());
    Map<String, Object> activity = activity(noteUri + "/activity", "Create", actorPublicId);
    activity.put("published", note.createdAt().truncatedTo(ChronoUnit.SECONDS).toString());
    activity.put("to", to(note, actorPublicId));
    activity.put("cc", cc(note, actorPublicId));
    activity.put("object", note(note, actorPublicId));
    return activity;
  }

  // Mastodon's addressing: public is to everyone and copied to followers; unlisted is to followers
  // and copied to everyone, which keeps it off public timelines; followers-only is to followers
  // alone. Direct notes are never delivered.
  private List<String> to(NoteSnapshot note, String actorPublicId) {
    return note.visibility() == NoteSnapshotReader.Visibility.PUBLIC
        ? List.of(ActivityStreams.PUBLIC)
        : List.of(urls.followers(actorPublicId));
  }

  private List<String> cc(NoteSnapshot note, String actorPublicId) {
    return switch (note.visibility()) {
      case PUBLIC -> List.of(urls.followers(actorPublicId));
      case UNLISTED -> List.of(ActivityStreams.PUBLIC);
      case PRIVATE, DIRECT -> List.of();
    };
  }

  public Map<String, Object> update(NoteSnapshot note, String actorPublicId) {
    long version = note.editedAt() == null ? 0 : note.editedAt().toEpochMilli();
    Map<String, Object> activity =
        activity(urls.note(note.id()) + "#updates/" + version, "Update", actorPublicId);
    activity.put("to", to(note, actorPublicId));
    activity.put("cc", cc(note, actorPublicId));
    activity.put("object", note(note, actorPublicId));
    return activity;
  }

  public Map<String, Object> announce(
      Long repostId, Long noteId, String actorPublicId, String noteAuthorPublicId) {
    Map<String, Object> activity = activity(urls.repost(repostId), "Announce", actorPublicId);
    activity.put("cc", List.of(urls.followers(actorPublicId), urls.actor(noteAuthorPublicId)));
    activity.put("object", urls.note(noteId));
    return activity;
  }

  public Map<String, Object> undoAnnounce(Long repostId, Long noteId, String actorPublicId) {
    Map<String, Object> announce = new LinkedHashMap<>();
    announce.put("id", urls.repost(repostId));
    announce.put("type", "Announce");
    announce.put("actor", urls.actor(actorPublicId));
    announce.put("object", urls.note(noteId));
    Map<String, Object> activity = activity(urls.repost(repostId) + "#undo", "Undo", actorPublicId);
    activity.put("cc", List.of(urls.followers(actorPublicId)));
    activity.put("object", announce);
    return activity;
  }

  public Map<String, Object> delete(Long noteId, String actorPublicId) {
    String noteUri = urls.note(noteId);
    Map<String, Object> activity = activity(noteUri + "#delete", "Delete", actorPublicId);
    activity.put("object", Map.of("id", noteUri, "type", "Tombstone"));
    return activity;
  }

  private Map<String, Object> activity(String id, String type, String actorPublicId) {
    Map<String, Object> activity = new LinkedHashMap<>();
    activity.put("@context", ActivityStreams.CONTEXT);
    activity.put("id", id);
    activity.put("type", type);
    activity.put("actor", urls.actor(actorPublicId));
    activity.put("to", List.of(ActivityStreams.PUBLIC));
    return activity;
  }

  String content(NoteSnapshot note) {
    StringBuilder html = new StringBuilder();
    for (String paragraph : note.body().strip().split("\\n\\s*\\n")) {
      if (paragraph.isBlank()) {
        continue;
      }
      html.append("<p>")
          .append(linkify(HtmlUtils.htmlEscape(paragraph.strip())).replace("\n", "<br>"))
          .append("</p>");
    }
    if (note.quote() != null) {
      String href = urls.blogPost(note.quote().authorUsername(), note.quote().slug());
      html.append("<p><a href=\"")
          .append(HtmlUtils.htmlEscape(href))
          .append("\">")
          .append(HtmlUtils.htmlEscape(note.quote().title()))
          .append("</a></p>");
    }
    if (note.quotedNote() != null) {
      String href =
          HtmlUtils.htmlEscape(
              urls.notePage(note.quotedNote().authorUsername(), note.quotedNote().id()));
      html.append("<p class=\"quote-inline\">RE: <a href=\"")
          .append(href)
          .append("\">")
          .append(href)
          .append("</a></p>");
    }
    return html.toString();
  }

  private String linkify(String escaped) {
    Matcher matcher = TOKEN.matcher(escaped);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(
          out,
          Matcher.quoteReplacement(
              matcher.group(1) != null ? link(matcher.group(1)) : hashtag(matcher)));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static String link(String candidate) {
    Matcher trailing = TRAILING_PUNCTUATION.matcher(candidate);
    String tail = trailing.find() ? trailing.group() : "";
    String link = candidate.substring(0, candidate.length() - tail.length());
    return "<a href=\""
        + link
        + "\" rel=\"nofollow noopener noreferrer\" target=\"_blank\">"
        + link
        + "</a>"
        + tail;
  }

  private String hashtag(Matcher matcher) {
    String name = Hashtags.nameAt(matcher, 2);
    if (name == null) {
      return matcher.group();
    }
    return "<a href=\""
        + HtmlUtils.htmlEscape(urls.tag(name))
        + "\" class=\"mention hashtag\" rel=\"tag\">#<span>"
        + name
        + "</span></a>"
        + matcher.group().substring(1 + name.length());
  }
}
