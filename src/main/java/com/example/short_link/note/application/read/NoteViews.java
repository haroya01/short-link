package com.example.short_link.note.application.read;

import com.example.short_link.common.note.Mentions;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteLinks;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.NotePollTally;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteViewerMarks;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRepository;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NotePollRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Batches every lookup for a page of notes: one query per kind of data, never per note. Notes by
// soft-deleted authors are dropped, and so are followers-only and direct notes this viewer may not
// read (one lookup, and only when the page has such notes by someone else). Mentioned members ride
// along in the authors query, so a mention links only when that member exists.
@Component
@RequiredArgsConstructor
public class NoteViews {

  private final NoteRepository notes;
  private final NoteMediaRepository media;
  private final NotePeopleReader people;
  private final QuotedPostReader quotedPosts;
  private final NoteLinkPreviewRepository linkPreviews;
  private final NotePollRepository polls;
  private final Clock clock;

  public List<NoteView> of(List<NoteEntity> page, Long viewerId) {
    if (page.isEmpty()) {
      return List.of();
    }
    Set<Long> quotedNoteIds =
        page.stream()
            .map(NoteEntity::getQuotedNoteId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    List<NoteEntity> quotedNotes =
        quotedNoteIds.isEmpty() ? List.of() : notes.findAllByIdIn(quotedNoteIds);
    Set<Long> authorIds = new HashSet<>();
    Set<Long> remoteIds = new HashSet<>();
    for (NoteEntity note : page) {
      collectAuthor(note, authorIds, remoteIds);
    }
    quotedNotes.forEach(quotedNote -> collectAuthor(quotedNote, authorIds, remoteIds));
    Set<String> handles =
        page.stream()
            .flatMap(note -> Mentions.of(note.getBody()).stream())
            .collect(Collectors.toSet());
    Map<Long, NoteAuthor> members =
        handles.isEmpty()
            ? people.activeAuthors(authorIds)
            : people.activeAuthors(authorIds, handles);
    Set<String> memberNames =
        members.values().stream().map(NoteAuthor::username).collect(Collectors.toSet());
    Authors authors =
        new Authors(members, remoteIds.isEmpty() ? Map.of() : people.remoteAuthors(remoteIds));
    Set<Long> restricted = new HashSet<>();
    for (NoteEntity note : page) {
      if (note.getVisibility().restricted() && !note.isOwnedBy(viewerId)) {
        restricted.add(note.getId());
      }
    }
    for (NoteEntity quoted : quotedNotes) {
      if (quoted.getVisibility().restricted() && !quoted.isOwnedBy(viewerId)) {
        restricted.add(quoted.getId());
      }
    }
    Set<Long> allowed = notes.visibleTo(viewerId, restricted);
    List<NoteEntity> visible =
        page.stream()
            .filter(note -> authors.of(note) != null)
            .filter(note -> !restricted.contains(note.getId()) || allowed.contains(note.getId()))
            .toList();
    if (visible.isEmpty()) {
      return List.of();
    }
    List<Long> ids = visible.stream().map(NoteEntity::getId).toList();
    Map<Long, NoteEntity> quotedById =
        quotedNotes.stream()
            .filter(quotedNote -> authors.of(quotedNote) != null)
            .filter(
                quotedNote ->
                    !restricted.contains(quotedNote.getId())
                        || allowed.contains(quotedNote.getId()))
            .collect(Collectors.toMap(NoteEntity::getId, quotedNote -> quotedNote));
    List<Long> imageNoteIds = new ArrayList<>(ids);
    imageNoteIds.addAll(quotedById.keySet());
    Map<Long, List<NoteView.Media>> images = images(imageNoteIds);
    Set<Long> quoted =
        visible.stream()
            .map(NoteEntity::getQuotedPostId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, QuotedPost> posts = quoted.isEmpty() ? Map.of() : quotedPosts.publishedByIds(quoted);
    Map<Long, NoteStats> stats = notes.stats(ids);
    NoteViewerMarks marks = notes.viewerMarks(viewerId, ids);
    Map<Long, NoteView.LinkPreview> cards = linkCards(visible);
    Map<Long, NoteView.Poll> pollViews = polls(visible, viewerId);

    List<NoteView> views = new ArrayList<>(visible.size());
    for (NoteEntity note : visible) {
      NoteStats counts = stats.getOrDefault(note.getId(), NoteStats.NONE);
      views.add(
          new NoteView(
              note.getId(),
              note.getBody(),
              note.getCreatedAt(),
              note.getEditedAt(),
              counts.likes(),
              viewerId == null ? null : marks.liked().contains(note.getId()),
              authors.of(note),
              images.getOrDefault(note.getId(), List.of()),
              note.getQuotedPostId() == null ? null : posts.get(note.getQuotedPostId()),
              note.getInReplyToId(),
              counts.replies(),
              counts.reposts(),
              viewerId == null ? null : marks.reposted().contains(note.getId()),
              quotedNote(quotedById.get(note.getQuotedNoteId()), authors, images),
              cards.get(note.getId()),
              null,
              counts.quotes(),
              viewerId == null ? null : marks.bookmarked().contains(note.getId()),
              Mentions.of(note.getBody()).stream().filter(memberNames::contains).toList(),
              note.getContentWarning(),
              note.isSensitive(),
              note.isPinned(),
              note.getVisibility().apiName(),
              pollViews.get(note.getId()),
              viewerId == null ? null : marks.muted().contains(note.getId())));
    }
    return views;
  }

  // Like link cards, a page without a poll costs no query.
  private Map<Long, NoteView.Poll> polls(List<NoteEntity> visible, Long viewerId) {
    List<NoteEntity> polled = visible.stream().filter(NoteEntity::hasPoll).toList();
    Map<Long, NoteView.Poll> views = new HashMap<>();
    if (polled.isEmpty()) {
      return views;
    }
    Map<Long, NotePollTally> tallies =
        polls.tallies(polled.stream().map(NoteEntity::getId).toList(), viewerId);
    Instant now = clock.instant();
    for (NoteEntity note : polled) {
      views.put(
          note.getId(),
          NotePolls.view(
              note, tallies.getOrDefault(note.getId(), NotePollTally.NONE), viewerId, now));
    }
    return views;
  }

  // Only notes whose body has an address can carry a card, so a page without one costs no query.
  private Map<Long, NoteView.LinkPreview> linkCards(List<NoteEntity> visible) {
    List<Long> linked =
        visible.stream()
            .filter(note -> NoteLinks.mayHavePreview(note.getBody()))
            .map(NoteEntity::getId)
            .toList();
    Map<Long, NoteView.LinkPreview> cards = new HashMap<>();
    if (linked.isEmpty()) {
      return cards;
    }
    for (var row : linkPreviews.findByNoteIds(linked)) {
      cards.put(
          row.getNoteId(),
          new NoteView.LinkPreview(
              row.getUrl(), row.getTitle(), row.getDescription(), row.getImageUrl()));
    }
    return cards;
  }

  private static void collectAuthor(NoteEntity note, Set<Long> authorIds, Set<Long> remoteIds) {
    if (note.isRemote()) {
      remoteIds.add(note.getRemoteActorId());
    } else {
      authorIds.add(note.getUserId());
    }
  }

  private record Authors(Map<Long, NoteAuthor> members, Map<Long, NoteAuthor> remote) {
    NoteAuthor of(NoteEntity note) {
      return note.isRemote() ? remote.get(note.getRemoteActorId()) : members.get(note.getUserId());
    }
  }

  private static NoteView.QuotedNote quotedNote(
      NoteEntity quoted, Authors authors, Map<Long, List<NoteView.Media>> images) {
    if (quoted == null) {
      return null;
    }
    return new NoteView.QuotedNote(
        quoted.getId(),
        quoted.getBody(),
        quoted.getCreatedAt(),
        authors.of(quoted),
        images.getOrDefault(quoted.getId(), List.of()),
        quoted.getContentWarning(),
        quoted.isSensitive());
  }

  private Map<Long, List<NoteView.Media>> images(List<Long> noteIds) {
    Map<Long, List<NoteView.Media>> byNote = new LinkedHashMap<>();
    for (NoteMediaEntity image : media.findByNoteIds(noteIds)) {
      byNote
          .computeIfAbsent(image.getNoteId(), id -> new ArrayList<>())
          .add(new NoteView.Media(image.getUrl(), image.getAltText(), image.getContentType()));
    }
    return byNote;
  }
}
