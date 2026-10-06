package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteQueryService {

  static final int MAX_PAGE_SIZE = 50;
  static final int MAX_REPLIES = 200;

  private final NoteRepository notes;
  private final NoteLikeRepository likes;
  private final NoteRepostRepository reposts;
  private final NotePeopleReader people;
  private final NoteViews views;

  // Everyone's notes, the original public feed. Older clients require likeCount, so a hidden count
  // is sent as 0; a signed-in reader also gets their own likes and their own notes' counts.
  @Transactional(readOnly = true)
  public NoteFeedView everyone(int page, int size, Long viewerId) {
    NoteFeedView feed = page(page, size, viewerId, notes::topLevel);
    return new NoteFeedView(
        feed.items().stream()
            .map(view -> view.likeCount() == null ? view.withLikeCount(0L) : view)
            .toList(),
        feed.page(),
        feed.hasNext());
  }

  @Transactional(readOnly = true)
  public NoteFeedView byAuthor(String username, int page, int size, Long viewerId) {
    NoteAuthor author =
        people
            .activeByUsername(username)
            .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_NOT_FOUND, username));
    return page(
        page,
        size,
        viewerId,
        (offset, limit) -> notes.topLevelByAuthors(List.of(author.id()), offset, limit));
  }

  @Transactional(readOnly = true)
  public NoteFeedView reposts(String username, int page, int size, Long viewerId) {
    NoteAuthor author =
        people
            .activeByUsername(username)
            .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_NOT_FOUND, username));
    return page(
        page,
        size,
        viewerId,
        (offset, limit) -> {
          List<Long> ids = reposts.recentNoteIdsByUser(author.id(), offset, limit);
          Map<Long, NoteEntity> found =
              notes.findAllByIdIn(ids).stream()
                  .collect(Collectors.toMap(NoteEntity::getId, Function.identity()));
          return ids.stream().map(found::get).filter(Objects::nonNull).toList();
        });
  }

  @Transactional(readOnly = true)
  public NoteFeedView following(Long viewerId, int page, int size) {
    List<Long> authors = new ArrayList<>(people.followingIds(viewerId));
    authors.add(viewerId);
    return page(
        page, size, viewerId, (offset, limit) -> notes.topLevelByAuthors(authors, offset, limit));
  }

  @Transactional(readOnly = true)
  public NoteThreadView thread(Long noteId, Long viewerId) {
    NoteEntity note =
        notes
            .findById(noteId)
            .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_NOT_FOUND, noteId));
    List<NoteEntity> batch = new ArrayList<>();
    batch.add(note);
    if (note.getInReplyToId() != null) {
      notes.findById(note.getInReplyToId()).ifPresent(batch::add);
    }
    batch.addAll(notes.replies(noteId, MAX_REPLIES));
    List<NoteView> loaded = views.of(batch, viewerId);
    NoteView main =
        loaded.stream()
            .filter(view -> view.id().equals(noteId))
            .findFirst()
            .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_NOT_FOUND, noteId));
    NoteView parent =
        note.getInReplyToId() == null
            ? null
            : loaded.stream()
                .filter(view -> view.id().equals(note.getInReplyToId()))
                .findFirst()
                .orElse(null);
    List<NoteView> replies =
        loaded.stream().filter(view -> noteId.equals(view.inReplyToId())).toList();
    return new NoteThreadView(main, parent, replies);
  }

  @Transactional(readOnly = true)
  public List<Long> likedNoteIds(Long userId, List<Long> noteIds) {
    return likes.likedNoteIds(userId, noteIds);
  }

  private NoteFeedView page(
      int page, int size, Long viewerId, BiFunction<Integer, Integer, List<NoteEntity>> fetch) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
    List<NoteEntity> rows = fetch.apply(safePage * safeSize, safeSize + 1);
    boolean hasNext = rows.size() > safeSize;
    List<NoteEntity> current = hasNext ? rows.subList(0, safeSize) : rows;
    return new NoteFeedView(views.of(current, viewerId), safePage, hasNext);
  }
}
