package com.example.short_link.federation.application;

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
    object.put("to", List.of(ActivityStreams.PUBLIC));
    object.put("cc", List.of(urls.followers(actorPublicId)));
    object.put("inReplyTo", note.inReplyToId() == null ? null : urls.note(note.inReplyToId()));
    object.put("sensitive", false);
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
    return object;
  }

  public Map<String, Object> create(NoteSnapshot note, String actorPublicId) {
    String noteUri = urls.note(note.id());
    Map<String, Object> activity = activity(noteUri + "/activity", "Create", actorPublicId);
    activity.put("published", note.createdAt().truncatedTo(ChronoUnit.SECONDS).toString());
    activity.put("cc", List.of(urls.followers(actorPublicId)));
    activity.put("object", note(note, actorPublicId));
    return activity;
  }

  public Map<String, Object> update(NoteSnapshot note, String actorPublicId) {
    long version = note.editedAt() == null ? 0 : note.editedAt().toEpochMilli();
    Map<String, Object> activity =
        activity(urls.note(note.id()) + "#updates/" + version, "Update", actorPublicId);
    activity.put("cc", List.of(urls.followers(actorPublicId)));
    activity.put("object", note(note, actorPublicId));
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
    return html.toString();
  }

  private static String linkify(String escaped) {
    Matcher matcher = URL.matcher(escaped);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String candidate = matcher.group();
      Matcher trailing = TRAILING_PUNCTUATION.matcher(candidate);
      String tail = trailing.find() ? trailing.group() : "";
      String link = candidate.substring(0, candidate.length() - tail.length());
      matcher.appendReplacement(
          out,
          Matcher.quoteReplacement(
              "<a href=\""
                  + link
                  + "\" rel=\"nofollow noopener noreferrer\" target=\"_blank\">"
                  + link
                  + "</a>"
                  + tail));
    }
    matcher.appendTail(out);
    return out.toString();
  }
}
