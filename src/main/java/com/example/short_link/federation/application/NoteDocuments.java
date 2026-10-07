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

  // Someone on another server the note addresses: the author it answers, or a person it names.
  public record Addressee(String actorUri, String handle, String profileUrl) {}

  public Map<String, Object> note(NoteSnapshot note, String actorPublicId) {
    return note(note, actorPublicId, null, List.of());
  }

  public Map<String, Object> note(
      NoteSnapshot note, String actorPublicId, RemoteParents.Parent parent) {
    return note(note, actorPublicId, parent, List.of());
  }

  public Map<String, Object> note(
      NoteSnapshot note, String actorPublicId, RemoteParents.Parent parent, List<Addressee> named) {
    List<Addressee> addressed = addressed(parent, named);
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("id", urls.note(note.id()));
    object.put("type", note.poll() == null ? "Note" : "Question");
    object.put("attributedTo", urls.actor(actorPublicId));
    object.put("content", content(note, named));
    object.put("published", note.createdAt().truncatedTo(ChronoUnit.SECONDS).toString());
    if (note.editedAt() != null) {
      object.put("updated", note.editedAt().truncatedTo(ChronoUnit.SECONDS).toString());
    }
    object.put("url", urls.notePage(note.authorUsername(), note.id()));
    object.put("to", to(note, actorPublicId, addressed));
    object.put("cc", cc(note, actorPublicId, addressed));
    object.put(
        "inReplyTo",
        parent != null
            ? parent.uri()
            : note.inReplyToId() == null ? null : urls.note(note.inReplyToId()));
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
    for (Addressee person : addressed) {
      Map<String, Object> mention = new LinkedHashMap<>();
      mention.put("type", "Mention");
      mention.put("href", person.actorUri());
      mention.put("name", person.handle());
      tags.add(mention);
    }
    for (String name : Hashtags.of(note.body())) {
      Map<String, Object> tag = new LinkedHashMap<>();
      tag.put("type", "Hashtag");
      tag.put("href", urls.tag(name));
      tag.put("name", "#" + name);
      tags.add(tag);
    }
    object.put("tag", tags);
    if (note.poll() != null) {
      poll(object, note.poll());
    }
    return object;
  }

  // Mastodon's Question: each option is a named Note whose replies count is its vote count.
  private static void poll(Map<String, Object> object, NoteSnapshotReader.Poll poll) {
    List<Map<String, Object>> options = new ArrayList<>();
    for (var option : poll.options()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("type", "Note");
      entry.put("name", option.title());
      entry.put("replies", Map.of("type", "Collection", "totalItems", option.votes()));
      options.add(entry);
    }
    object.put(poll.multiple() ? "anyOf" : "oneOf", options);
    String end = poll.endTime().truncatedTo(ChronoUnit.SECONDS).toString();
    object.put("endTime", end);
    if (poll.closed()) {
      object.put("closed", end);
    }
    object.put("votersCount", poll.voters());
  }

  public Map<String, Object> pollEnded(NoteSnapshot note, String actorPublicId) {
    Map<String, Object> activity =
        activity(urls.note(note.id()) + "#updates/poll-ended", "Update", actorPublicId);
    activity.put("to", to(note, actorPublicId));
    activity.put("cc", cc(note, actorPublicId));
    activity.put("object", note(note, actorPublicId));
    return activity;
  }

  public Map<String, Object> create(NoteSnapshot note, String actorPublicId) {
    return create(note, actorPublicId, null, List.of());
  }

  public Map<String, Object> create(
      NoteSnapshot note, String actorPublicId, RemoteParents.Parent parent, List<Addressee> named) {
    List<Addressee> addressed = addressed(parent, named);
    String noteUri = urls.note(note.id());
    Map<String, Object> activity = activity(noteUri + "/activity", "Create", actorPublicId);
    activity.put("published", note.createdAt().truncatedTo(ChronoUnit.SECONDS).toString());
    activity.put("to", to(note, actorPublicId, addressed));
    activity.put("cc", cc(note, actorPublicId, addressed));
    activity.put("object", note(note, actorPublicId, parent, named));
    return activity;
  }

  private static List<Addressee> addressed(RemoteParents.Parent parent, List<Addressee> named) {
    Map<String, Addressee> byActor = new LinkedHashMap<>();
    if (parent != null) {
      byActor.put(parent.actorUri(), new Addressee(parent.actorUri(), parent.handle(), null));
    }
    for (Addressee person : named) {
      byActor.putIfAbsent(person.actorUri(), person);
    }
    return List.copyOf(byActor.values());
  }

  // Mastodon's addressing: public is to everyone and copied to followers; unlisted is to followers
  // and copied to everyone, which keeps it off public timelines; followers-only is to followers
  // alone. Direct notes leave only as a reply to someone elsewhere, addressed to that person alone;
  // any other reply to them copies them in.
  private List<String> to(NoteSnapshot note, String actorPublicId) {
    return to(note, actorPublicId, List.of());
  }

  private List<String> to(NoteSnapshot note, String actorPublicId, List<Addressee> addressed) {
    if (!addressed.isEmpty() && note.visibility() == NoteSnapshotReader.Visibility.DIRECT) {
      return addressed.stream().map(Addressee::actorUri).toList();
    }
    return note.visibility() == NoteSnapshotReader.Visibility.PUBLIC
        ? List.of(ActivityStreams.PUBLIC)
        : List.of(urls.followers(actorPublicId));
  }

  private List<String> cc(NoteSnapshot note, String actorPublicId) {
    return cc(note, actorPublicId, List.of());
  }

  private List<String> cc(NoteSnapshot note, String actorPublicId, List<Addressee> addressed) {
    List<String> cc =
        new ArrayList<>(
            switch (note.visibility()) {
              case PUBLIC -> List.of(urls.followers(actorPublicId));
              case UNLISTED -> List.of(ActivityStreams.PUBLIC);
              case PRIVATE, DIRECT -> List.<String>of();
            });
    if (note.visibility() != NoteSnapshotReader.Visibility.DIRECT) {
      addressed.forEach(person -> cc.add(person.actorUri()));
    }
    return cc;
  }

  public Map<String, Object> update(NoteSnapshot note, String actorPublicId) {
    return update(note, actorPublicId, null, List.of());
  }

  public Map<String, Object> update(
      NoteSnapshot note, String actorPublicId, RemoteParents.Parent parent, List<Addressee> named) {
    List<Addressee> addressed = addressed(parent, named);
    long version = note.editedAt() == null ? 0 : note.editedAt().toEpochMilli();
    Map<String, Object> activity =
        activity(urls.note(note.id()) + "#updates/" + version, "Update", actorPublicId);
    activity.put("to", to(note, actorPublicId, addressed));
    activity.put("cc", cc(note, actorPublicId, addressed));
    activity.put("object", note(note, actorPublicId, parent, named));
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

  public Map<String, Object> announceRemote(
      Long repostId, RemoteParents.Parent note, String actorPublicId) {
    Map<String, Object> activity = activity(urls.repost(repostId), "Announce", actorPublicId);
    activity.put("cc", List.of(urls.followers(actorPublicId), note.actorUri()));
    activity.put("object", note.uri());
    return activity;
  }

  public Map<String, Object> undoAnnounceRemote(
      Long repostId, RemoteParents.Parent note, String actorPublicId) {
    Map<String, Object> announce = new LinkedHashMap<>();
    announce.put("id", urls.repost(repostId));
    announce.put("type", "Announce");
    announce.put("actor", urls.actor(actorPublicId));
    announce.put("object", note.uri());
    Map<String, Object> activity = activity(urls.repost(repostId) + "#undo", "Undo", actorPublicId);
    activity.put("cc", List.of(urls.followers(actorPublicId), note.actorUri()));
    activity.put("object", announce);
    return activity;
  }

  // Each like and undo gets an id of its own: the delivery queue keeps one copy per id and inbox,
  // so liking again after an undo must not reuse the first like's id.
  public Map<String, Object> like(
      Long noteId, RemoteParents.Parent note, String actorPublicId, long at, boolean liked) {
    String likeId = urls.actor(actorPublicId) + "#likes/" + noteId + "/" + at;
    Map<String, Object> like = new LinkedHashMap<>();
    like.put("@context", ActivityStreams.CONTEXT);
    like.put("id", likeId);
    like.put("type", "Like");
    like.put("actor", urls.actor(actorPublicId));
    like.put("object", note.uri());
    if (liked) {
      return like;
    }
    like.remove("@context");
    Map<String, Object> undo = new LinkedHashMap<>();
    undo.put("@context", ActivityStreams.CONTEXT);
    undo.put("id", likeId + "/undo");
    undo.put("type", "Undo");
    undo.put("actor", urls.actor(actorPublicId));
    undo.put("object", like);
    return undo;
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
    return content(note, List.of());
  }

  // A named account becomes Mastodon's h-card mention link, so its server shows it as a mention.
  String content(NoteSnapshot note, List<Addressee> named) {
    StringBuilder html = new StringBuilder();
    for (String paragraph : note.body().strip().split("\\n\\s*\\n")) {
      if (paragraph.isBlank()) {
        continue;
      }
      String escaped = linkify(HtmlUtils.htmlEscape(paragraph.strip()));
      for (Addressee person : named) {
        if (person.profileUrl() == null) {
          continue;
        }
        String handle = HtmlUtils.htmlEscape(person.handle());
        String user =
            HtmlUtils.htmlEscape(person.handle().substring(1, person.handle().indexOf('@', 1)));
        escaped =
            escaped.replace(
                handle,
                "<span class=\"h-card\"><a href=\""
                    + HtmlUtils.htmlEscape(person.profileUrl())
                    + "\" class=\"u-url mention\">@<span>"
                    + user
                    + "</span></a></span>");
      }
      html.append("<p>").append(escaped.replace("\n", "<br>")).append("</p>");
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
