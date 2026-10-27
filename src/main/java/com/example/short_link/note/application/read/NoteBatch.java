package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteViewerMarks;
import com.example.short_link.note.domain.QuotedPost;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record NoteBatch(
    List<NoteEntity> visible,
    Map<Long, NoteAuthor> members,
    Map<Long, NoteAuthor> remoteAuthors,
    Set<String> memberNames,
    Map<Long, List<NoteView.Media>> images,
    Map<Long, NoteEntity> quotedNotes,
    Map<Long, QuotedPost> quotedPosts,
    Map<Long, NoteStats> stats,
    NoteViewerMarks marks,
    Map<Long, NoteView.LinkPreview> cards,
    Map<Long, NoteView.Poll> polls,
    Set<Long> closedThreads,
    Map<Long, String> roomNames) {

  static final NoteBatch EMPTY =
      new NoteBatch(
          List.of(),
          Map.of(),
          Map.of(),
          Set.of(),
          Map.of(),
          Map.of(),
          Map.of(),
          Map.of(),
          NoteViewerMarks.NONE,
          Map.of(),
          Map.of(),
          Set.of(),
          Map.of());

  public static NoteBatch justWritten(
      NoteEntity note,
      Map<Long, NoteAuthor> members,
      NoteEntity quotedNote,
      NoteAuthor quotedAuthor,
      QuotedPost quotedPost,
      Map<Long, List<NoteView.Media>> images,
      NoteView.Poll poll,
      Map<Long, String> roomNames) {
    return new NoteBatch(
        List.of(note),
        members,
        quotedNote != null && quotedNote.isRemote()
            ? Map.of(quotedNote.getRemoteActorId(), quotedAuthor)
            : Map.of(),
        names(members),
        images,
        quotedNote == null ? Map.of() : Map.of(quotedNote.getId(), quotedNote),
        quotedPost == null ? Map.of() : Map.of(note.getQuotedPostId(), quotedPost),
        Map.of(),
        NoteViewerMarks.NONE,
        Map.of(),
        poll == null ? Map.of() : Map.of(note.getId(), poll),
        Set.of(),
        roomNames);
  }

  static Set<String> names(Map<Long, NoteAuthor> members) {
    return members.values().stream().map(NoteAuthor::username).collect(Collectors.toSet());
  }

  NoteAuthor authorOf(NoteEntity note) {
    return note.isRemote()
        ? remoteAuthors.get(note.getRemoteActorId())
        : members.get(note.getUserId());
  }
}
