package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteLinks;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRepository;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
import com.example.short_link.note.domain.repository.QuotedPostReader;
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
// soft-deleted authors are dropped.
@Component
@RequiredArgsConstructor
public class NoteViews {

  private final NoteRepository notes;
  private final NoteLikeRepository likes;
  private final NoteMediaRepository media;
  private final NotePeopleReader people;
  private final QuotedPostReader quotedPosts;
  private final NoteRepostRepository reposts;
  private final NoteLinkPreviewRepository linkPreviews;

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
    Set<Long> authorIds = page.stream().map(NoteEntity::getUserId).collect(Collectors.toSet());
    quotedNotes.forEach(quotedNote -> authorIds.add(quotedNote.getUserId()));
    Map<Long, NoteAuthor> authors = people.activeAuthors(authorIds);
    List<NoteEntity> visible =
        page.stream().filter(note -> authors.containsKey(note.getUserId())).toList();
    if (visible.isEmpty()) {
      return List.of();
    }
    List<Long> ids = visible.stream().map(NoteEntity::getId).toList();
    Map<Long, NoteEntity> quotedById =
        quotedNotes.stream()
            .filter(quotedNote -> authors.containsKey(quotedNote.getUserId()))
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
    Set<Long> liked =
        viewerId == null ? Set.of() : new HashSet<>(likes.likedNoteIds(viewerId, ids));
    Set<Long> reposted =
        viewerId == null ? Set.of() : new HashSet<>(reposts.repostedNoteIds(viewerId, ids));
    Map<Long, NoteView.LinkPreview> cards = linkCards(visible);

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
              viewerId == null ? null : liked.contains(note.getId()),
              authors.get(note.getUserId()),
              images.getOrDefault(note.getId(), List.of()),
              note.getQuotedPostId() == null ? null : posts.get(note.getQuotedPostId()),
              note.getInReplyToId(),
              counts.replies(),
              counts.reposts(),
              viewerId == null ? null : reposted.contains(note.getId()),
              quotedNote(quotedById.get(note.getQuotedNoteId()), authors, images),
              cards.get(note.getId())));
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

  private static NoteView.QuotedNote quotedNote(
      NoteEntity quoted, Map<Long, NoteAuthor> authors, Map<Long, List<NoteView.Media>> images) {
    if (quoted == null) {
      return null;
    }
    return new NoteView.QuotedNote(
        quoted.getId(),
        quoted.getBody(),
        quoted.getCreatedAt(),
        authors.get(quoted.getUserId()),
        images.getOrDefault(quoted.getId(), List.of()));
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
