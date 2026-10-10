package com.example.short_link.note.application.read;

import com.example.short_link.common.note.Hashtags;
import com.example.short_link.note.application.write.NoteCommandService;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.repository.NoteBookmarkRepository;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
import com.example.short_link.note.domain.repository.NoteSeriesReader;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
  private final NoteSeriesReader noteSeries;

  @Transactional(readOnly = true)
  public NoteFeedView everyone(int page, int size, Long viewerId) {
    return page(page, size, viewerId, (offset, limit) -> notes.topLevel(viewerId, offset, limit));
  }

  @Transactional(readOnly = true)
  public NoteFeedView byRemoteActor(Long remoteActorId, int page, int size, Long viewerId) {
    return page(
        page,
        size,
        viewerId,
        (offset, limit) -> notes.byRemoteActor(remoteActorId, viewerId, offset, limit));
  }

  @Transactional(readOnly = true)
  public NoteFeedView direct(Long viewerId, int page, int size) {
    return page(page, size, viewerId, (offset, limit) -> notes.direct(viewerId, offset, limit));
  }

  @Transactional(readOnly = true)
  public NoteFeedView trending(int page, int size, Long viewerId) {
    return page(page, size, viewerId, (offset, limit) -> notes.trending(viewerId, offset, limit));
  }

  // Mastodon's trending hashtags: a week, used by at least two accounts, the top ten.
  static final int TREND_DAYS = 7;
  static final int TREND_MIN_ACCOUNTS = 2;
  static final int TREND_LIMIT = 10;

  @Transactional(readOnly = true)
  public List<TrendingTagView> trendingTags() {
    return notes.trendingTags(Instant.now(), TREND_DAYS, TREND_MIN_ACCOUNTS, TREND_LIMIT).stream()
        .map(TrendingTagView::of)
        .toList();
  }

  // Mastodon's trending links: same window and floor as hashtags.
  @Transactional(readOnly = true)
  public List<TrendingLinkView> trendingLinks() {
    return notes.trendingLinks(Instant.now(), TREND_DAYS, TREND_MIN_ACCOUNTS, TREND_LIMIT).stream()
        .map(TrendingLinkView::of)
        .toList();
  }

  @Transactional(readOnly = true)
  public NoteFeedView linked(String url, int page, int size, Long viewerId) {
    if (url == null || url.isBlank()) {
      return new NoteFeedView(List.of(), Math.max(page, 0), false);
    }
    return page(
        page, size, viewerId, (offset, limit) -> notes.linked(url, viewerId, offset, limit));
  }

  @Transactional(readOnly = true)
  public NoteFeedView federated(Long viewerId, int page, int size) {
    return page(page, size, viewerId, (offset, limit) -> notes.federated(viewerId, offset, limit));
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
        (offset, limit) ->
            inOrder(reposts.recentNoteIdsByUser(author.id(), viewerId, offset, limit)));
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

  @Transactional(readOnly = true)
  public PostQuotesView postQuotes(Long postId, int page, int size, Long viewerId) {
    NoteFeedView feed =
        page(
            page,
            size,
            viewerId,
            (offset, limit) -> notes.quotesOfPost(postId, viewerId, offset, limit));
    return new PostQuotesView(
        feed.items(), feed.page(), feed.hasNext(), notes.countQuotesOfPost(postId, viewerId));
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
        views.ofFeed(current.stream().map(NoteFeedRow::note).toList(), viewerId).stream()
            .collect(Collectors.toMap(NoteView::id, Function.identity(), (first, again) -> first));
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
  // It opens for whoever may open the thread: not across a block, not once the writer is gone or
  // their server suspended, and a restricted note only for its readers.
  @Transactional(readOnly = true)
  public NoteHistoryView history(Long noteId, Long viewerId) {
    NoteEntity note =
        notes
            .findUnblocked(noteId, viewerId)
            .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_NOT_FOUND, noteId));
    boolean authorGone =
        note.isRemote()
            ? people.remoteAuthors(Set.of(note.getRemoteActorId())).isEmpty()
            : people.activeAuthors(Set.of(note.getUserId())).isEmpty();
    if (authorGone
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
            .findUnblocked(noteId, viewerId)
            .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_NOT_FOUND, noteId));
    List<NoteEntity> batch = new ArrayList<>();
    batch.add(note);
    NoteEntity parentNote =
        note.getInReplyToId() == null
            ? null
            : notes.findUnblocked(note.getInReplyToId(), viewerId).orElse(null);
    if (parentNote != null) {
      batch.add(parentNote);
    }
    batch.addAll(notes.replies(noteId, viewerId, MAX_REPLIES));
    List<NoteEntity> hidden = notes.hiddenReplies(noteId, viewerId, MAX_REPLIES);
    batch.addAll(hidden);
    Set<Long> hiddenIds = hidden.stream().map(NoteEntity::getId).collect(Collectors.toSet());
    List<Long> chain =
        NoteViews.chains(
                notes.selfReplies(List.of(noteId), NoteCommandService.MAX_THREAD_NOTES - 1))
            .getOrDefault(noteId, List.of());
    Set<Long> inBatch = batch.stream().map(NoteEntity::getId).collect(Collectors.toSet());
    List<Long> missing = chain.stream().filter(id -> !inBatch.contains(id)).toList();
    if (!missing.isEmpty()) {
      batch.addAll(notes.findAllByIdIn(missing));
    }
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
    Map<Long, NoteView> byId = new HashMap<>();
    loaded.forEach(view -> byId.putIfAbsent(view.id(), view));
    List<NoteView> continuation =
        chain.stream().map(byId::get).takeWhile(Objects::nonNull).toList();
    Set<Long> parts = continuation.stream().map(NoteView::id).collect(Collectors.toSet());
    List<NoteView> replies =
        loaded.stream()
            .filter(view -> noteId.equals(view.inReplyToId()))
            .filter(view -> !parts.contains(view.id()))
            .filter(view -> !hiddenIds.contains(view.id()))
            .toList();
    int hiddenReplyCount =
        (int) loaded.stream().filter(view -> hiddenIds.contains(view.id())).count();
    return new NoteThreadView(
        main,
        parent,
        replies,
        continuation,
        seriesNav(note),
        hiddenReplyCount,
        viewerId != null && writesThread(viewerId, note, parentNote));
  }

  private boolean writesThread(Long viewerId, NoteEntity note, NoteEntity parent) {
    Long root = note.conversation();
    if (root.equals(note.getId())) {
      return note.isOwnedBy(viewerId);
    }
    if (parent != null && root.equals(parent.getId())) {
      return parent.isOwnedBy(viewerId);
    }
    return notes.findById(root).filter(first -> first.isOwnedBy(viewerId)).isPresent();
  }

  // Threads' hidden replies: the replies the thread's writer moved out of the thread under this
  // note, for anyone who may open the note itself.
  @Transactional(readOnly = true)
  public List<NoteView> hiddenReplies(Long noteId, Long viewerId) {
    NoteEntity note =
        notes
            .findUnblocked(noteId, viewerId)
            .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_NOT_FOUND, noteId));
    List<NoteEntity> batch = new ArrayList<>();
    batch.add(note);
    batch.addAll(notes.hiddenReplies(noteId, viewerId, MAX_REPLIES));
    List<NoteView> loaded = views.of(batch, viewerId);
    if (loaded.stream().noneMatch(view -> view.id().equals(noteId))) {
      throw new NoteException(NoteErrorCode.NOTE_NOT_FOUND, noteId);
    }
    return loaded.stream().filter(view -> !view.id().equals(noteId)).toList();
  }

  private NoteSeriesNavView seriesNav(NoteEntity note) {
    if (note.isRemote() || note.getVisibility().restricted()) {
      return null;
    }
    return noteSeries
        .containing(note.getId())
        .map(series -> NoteSeriesNavView.of(series, note.getId()))
        .orElse(null);
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
    return new NoteFeedView(views.ofFeed(current, viewerId), safePage, hasNext);
  }
}
