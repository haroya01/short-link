package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import java.util.ArrayList;
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

  public List<NoteView> of(List<NoteEntity> page, Long viewerId) {
    if (page.isEmpty()) {
      return List.of();
    }
    Map<Long, NoteAuthor> authors =
        people.activeAuthors(page.stream().map(NoteEntity::getUserId).collect(Collectors.toSet()));
    List<NoteEntity> visible =
        page.stream().filter(note -> authors.containsKey(note.getUserId())).toList();
    if (visible.isEmpty()) {
      return List.of();
    }
    List<Long> ids = visible.stream().map(NoteEntity::getId).toList();
    Map<Long, List<NoteView.Media>> images = images(ids);
    Set<Long> quoted =
        visible.stream()
            .map(NoteEntity::getQuotedPostId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, QuotedPost> posts = quoted.isEmpty() ? Map.of() : quotedPosts.publishedByIds(quoted);
    Map<Long, Long> replies = notes.replyCounts(ids);
    List<Long> own =
        viewerId == null
            ? List.of()
            : visible.stream()
                .filter(note -> note.isOwnedBy(viewerId))
                .map(NoteEntity::getId)
                .toList();
    Map<Long, Long> likeCounts = own.isEmpty() ? Map.of() : likes.counts(own);
    Set<Long> liked =
        viewerId == null ? Set.of() : new HashSet<>(likes.likedNoteIds(viewerId, ids));

    List<NoteView> views = new ArrayList<>(visible.size());
    for (NoteEntity note : visible) {
      boolean mine = viewerId != null && note.isOwnedBy(viewerId);
      views.add(
          new NoteView(
              note.getId(),
              note.getBody(),
              note.getCreatedAt(),
              note.getEditedAt(),
              mine ? likeCounts.getOrDefault(note.getId(), 0L) : null,
              viewerId == null ? null : liked.contains(note.getId()),
              authors.get(note.getUserId()),
              images.getOrDefault(note.getId(), List.of()),
              note.getQuotedPostId() == null ? null : posts.get(note.getQuotedPostId()),
              note.getInReplyToId(),
              replies.getOrDefault(note.getId(), 0L)));
    }
    return views;
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
