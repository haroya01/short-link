package com.example.short_link.note.application.read;

import com.example.short_link.common.note.Hashtags;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.repository.NoteBookmarkRepository;
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
import java.util.Set;
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
  private final NoteBookmarkRepository bookmarks;
  private final NotePeopleReader people;
  private final NoteViews views;

  @Transactional(readOnly = true)
  public NoteFeedView everyone(int page, int size, Long viewerId) {
    return page(page, size, viewerId, (offset, limit) -> notes.topLevel(viewerId, offset, limit));
  }

  @Transactional(readOnly = true)
  public NoteFeedView direct(Long viewerId, int page, int size) {
    return page(page, size, viewerId, (offset, limit) -> notes.direct(viewerId, offset, limit));
  }

  @Transactional(readOnly = true)
  public NoteFeedView trending(int page, int size, Long viewerId) {
    return page(page, size, viewerId, (offset, limit) -> notes.trending(viewerId, offset, limit));
  }

  @Transactional(readOnly = true)
  public NoteFeedView tagged(String tag, int page, int size, Long viewerId) {
    String name = tag.startsWith("#") ? tag.substring(1) : tag;
    if (name.isBlank() || name.length() > Hashtags.MAX_LENGTH) {
      return new NoteFeedView(List.of(), Math.max(page, 0), false);
    }
    return page(
        page, size, viewerId, (offset, limit) -> notes.tagged(name, viewerId, offset, limit));
  }

  @Transactional(readOnly = true)
  public NoteFeedView search(String query, int page, int size, Long viewerId) {
    String trimmed = query == null ? "" : query.strip();
    if (trimmed.isEmpty() || trimmed.length() > NoteSearchTerms.MAX_LENGTH) {
      return new NoteFeedView(List.of(), Math.max(page, 0), false);
    }
    NoteSearchTerms terms = NoteSearchTerms.of(trimmed);
    return page(
        page,
        size,
        viewerId,
        (offset, limit) -> notes.search(terms.match(), terms.like(), viewerId, offset, limit));
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
        (offset, limit) -> notes.topLevelByAuthor(author.id(), viewerId, offset, limit));
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
        (offset, limit) -> inOrder(reposts.recentNoteIdsByUser(author.id(), offset, limit)));
  }

  // A repost shows under its reposter; if the reposter's account is gone the note leaves the feed
  // with it, since it only came in through them.
  @Transactional(readOnly = true)
  public NoteFeedView bookmarks(Long userId, int page, int size) {
    return page(
        page,
        size,
        userId,
        (offset, limit) -> inOrder(bookmarks.recentNoteIdsByUser(userId, offset, limit)));
  }

  @Transactional(readOnly = true)
  public NoteFeedView quotes(Long noteId, int page, int size, Long viewerId) {
    return page(
        page, size, viewerId, (offset, limit) -> notes.quotesOf(noteId, viewerId, offset, limit));
  }

  private List<NoteEntity> inOrder(List<Long> ids) {
    Map<Long, NoteEntity> found =
        notes.findAllByIdIn(ids).stream()
            .collect(Collectors.toMap(NoteEntity::getId, Function.identity()));
    return ids.stream().map(found::get).filter(Objects::nonNull).toList();
  }

  @Transactional(readOnly = true)
  public NoteFeedView following(Long viewerId, int page, int size) {
    List<Long> authors = new ArrayList<>(people.followingIds(viewerId));
    authors.add(viewerId);
    int safePage = Math.max(page, 0);
    int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
    List<NoteFeedRow> rows = notes.following(authors, viewerId, safePage * safeSize, safeSize + 1);
    boolean hasNext = rows.size() > safeSize;
    List<NoteFeedRow> current = hasNext ? rows.subList(0, safeSize) : rows;
    Set<Long> reposterIds =
        current.stream()
            .map(NoteFeedRow::reposterId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, NoteAuthor> reposters =
        reposterIds.isEmpty() ? Map.of() : people.activeAuthors(reposterIds);
    Map<Long, NoteView> byId =
        views.of(current.stream().map(NoteFeedRow::note).toList(), viewerId).stream()
            .collect(Collectors.toMap(NoteView::id, Function.identity()));
    List<NoteView> items = new ArrayList<>(current.size());
    for (NoteFeedRow row : current) {
      NoteView view = byId.get(row.note().getId());
      if (view == null) {
        continue;
      }
      if (row.reposterId() == null) {
        items.add(view);
      } else if (reposters.containsKey(row.reposterId())) {
        items.add(view.withRepostedBy(reposters.get(row.reposterId())));
      }
    }
    return new NoteFeedView(items, safePage, hasNext);
  }

  // Mastodon's edit history: the note as it reads now, then each earlier version, newest first.
  @Transactional(readOnly = true)
  public NoteHistoryView history(Long noteId, Long viewerId) {
    NoteEntity note =
        notes
            .findById(noteId)
            .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_NOT_FOUND, noteId));
    if (people.activeAuthors(Set.of(note.getUserId())).isEmpty()
        || (note.getVisibility().restricted()
            && !note.isOwnedBy(viewerId)
            && !notes.visibleTo(viewerId, Set.of(noteId)).contains(noteId))) {
      throw new NoteException(NoteErrorCode.NOTE_NOT_FOUND, noteId);
    }
    List<NoteHistoryView.Version> versions = new ArrayList<>();
    versions.add(
        new NoteHistoryView.Version(
            note.getBody(),
            note.getContentWarning(),
            note.isSensitive(),
            note.getEditedAt() == null ? note.getCreatedAt() : note.getEditedAt()));
    for (var earlier : notes.versions(noteId)) {
      versions.add(
          new NoteHistoryView.Version(
              earlier.body(), earlier.contentWarning(), earlier.sensitive(), earlier.at()));
    }
    return new NoteHistoryView(noteId, versions);
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
    batch.addAll(notes.replies(noteId, viewerId, MAX_REPLIES));
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
