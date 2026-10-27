package com.example.short_link.note.application.read;

import com.example.short_link.common.note.Mentions;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.note.application.access.NoteAccess;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteLinks;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.NotePollTally;
import com.example.short_link.note.domain.NoteReplyPolicy;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteViewerMarks;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.ReplyGate;
import com.example.short_link.note.domain.SelfReply;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRepository;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePageReader;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NotePollRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRoomReader;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  private final NotePageReader pages;
  private final NoteAccess access;
  private final NoteRoomReader rooms;
  private final NoteMediaRepository media;
  private final NotePeopleReader people;
  private final QuotedPostReader quotedPosts;
  private final NoteLinkPreviewRepository linkPreviews;
  private final NotePollRepository polls;
  private final Clock clock;

  public List<NoteView> of(List<NoteEntity> page, Long viewerId) {
    NoteBatch loaded = load(page, viewerId);
    List<NoteView> views = new ArrayList<>(loaded.visible().size());
    for (NoteEntity note : loaded.visible()) {
      views.add(assemble(note, loaded, viewerId));
    }
    return views;
  }

  private NoteBatch load(List<NoteEntity> page, Long viewerId) {
    if (page.isEmpty()) {
      return NoteBatch.EMPTY;
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
    Map<Long, NoteEntity> limitedRoots = limitedRoots(page, viewerId);
    Set<String> handles =
        Stream.concat(page.stream(), limitedRoots.values().stream())
            .flatMap(note -> Mentions.of(note.getBody()).stream())
            .collect(Collectors.toSet());
    Map<Long, NoteAuthor> members =
        handles.isEmpty()
            ? people.activeAuthors(authorIds)
            : people.activeAuthors(authorIds, handles);
    Set<String> memberNames = NoteBatch.names(members);
    Authors authors =
        new Authors(
            members, remoteIds.isEmpty() ? Map.of() : people.remoteAuthors(remoteIds, viewerId));
    List<NoteEntity> candidates = new ArrayList<>(page);
    candidates.addAll(quotedNotes);
    Set<Long> readable = access.readable(candidates, viewerId);
    List<NoteEntity> visible =
        page.stream()
            .filter(note -> authors.of(note) != null)
            .filter(note -> readable.contains(note.getId()))
            .toList();
    if (visible.isEmpty()) {
      return NoteBatch.EMPTY;
    }
    List<Long> ids = visible.stream().map(NoteEntity::getId).toList();
    Map<Long, NoteEntity> quotedById =
        quotedNotes.stream()
            .filter(quotedNote -> authors.of(quotedNote) != null)
            .filter(quotedNote -> readable.contains(quotedNote.getId()))
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
    Map<Long, NoteStats> stats = pages.stats(ids);
    NoteViewerMarks marks = pages.viewerMarks(viewerId, ids);
    Map<Long, NoteView.LinkPreview> cards = linkCards(visible);
    Map<Long, NoteView.Poll> pollViews = polls(visible, viewerId);
    Set<Long> closedThreads = closedTo(viewerId, limitedRoots, members);
    Map<Long, String> roomNames =
        rooms.names(
            visible.stream()
                .map(NoteEntity::getRoomId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    return new NoteBatch(
        visible,
        members,
        authors.remote(),
        memberNames,
        images,
        quotedById,
        posts,
        stats,
        marks,
        cards,
        pollViews,
        closedThreads,
        roomNames);
  }

  public static NoteView assemble(NoteEntity note, NoteBatch batch, Long viewerId) {
    NoteStats counts = batch.stats().getOrDefault(note.getId(), NoteStats.NONE);
    NoteViewerMarks marks = batch.marks();
    return new NoteView(
        note.getId(),
        note.getBody(),
        note.getCreatedAt(),
        note.getEditedAt(),
        counts.likes(),
        viewerId == null ? null : marks.liked().contains(note.getId()),
        batch.authorOf(note),
        batch.images().getOrDefault(note.getId(), List.of()),
        note.getQuotedPostId() == null ? null : batch.quotedPosts().get(note.getQuotedPostId()),
        note.getInReplyToId(),
        counts.replies(),
        counts.reposts(),
        viewerId == null ? null : marks.reposted().contains(note.getId()),
        note.getQuotedNoteId() == null
            ? null
            : quotedNote(batch.quotedNotes().get(note.getQuotedNoteId()), batch),
        batch.cards().get(note.getId()),
        null,
        counts.quotes(),
        viewerId == null ? null : marks.bookmarked().contains(note.getId()),
        Mentions.of(note.getBody()).stream().filter(batch.memberNames()::contains).toList(),
        note.getContentWarning(),
        note.isSensitive(),
        note.isPinned(),
        note.getVisibility().apiName(),
        batch.polls().get(note.getId()),
        viewerId == null ? null : marks.muted().contains(note.getId()),
        note.getLanguage(),
        null,
        note.getReplyPolicy().apiName(),
        viewerId == null ? null : !batch.closedThreads().contains(note.conversation()),
        note.isReplyHidden(),
        remote(note),
        note.getRoomId() == null
            ? null
            : new NoteView.Room(note.getRoomId(), batch.roomNames().get(note.getRoomId())));
  }

  private static NoteView.Remote remote(NoteEntity note) {
    if (!note.isRemote()) {
      return null;
    }
    String url = note.getRemoteUrl();
    return new NoteView.Remote(
        note.getRemoteKind() == null ? RemoteNotes.Kind.NOTE.name() : note.getRemoteKind(),
        note.getRemoteTitle(),
        web(url) ? url : note.getUri(),
        note.getRemoteCommunity());
  }

  private static boolean web(String url) {
    String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
    return lower.startsWith("https://") || lower.startsWith("http://");
  }

  // The first notes of the page's threads that limit replies, read only when the viewer is signed
  // in and the page holds such a thread; a first note already on the page is not read again.
  private Map<Long, NoteEntity> limitedRoots(List<NoteEntity> page, Long viewerId) {
    if (viewerId == null) {
      return Map.of();
    }
    Map<Long, NoteEntity> onPage = new HashMap<>();
    page.forEach(note -> onPage.put(note.getId(), note));
    Map<Long, NoteEntity> roots = new HashMap<>();
    Set<Long> missing = new HashSet<>();
    for (NoteEntity note : page) {
      if (!note.getReplyPolicy().limited()) {
        continue;
      }
      NoteEntity root = onPage.get(note.conversation());
      if (root == null) {
        missing.add(note.conversation());
      } else {
        roots.put(root.getId(), root);
      }
    }
    if (!missing.isEmpty()) {
      notes.findAllByIdIn(missing).forEach(root -> roots.put(root.getId(), root));
    }
    return roots;
  }

  // Of those threads, the ones ReplyGate closes to this viewer. Whether their writers follow the
  // viewer is one lookup for every writer of a thread still closed without it.
  private Set<Long> closedTo(
      Long viewerId, Map<Long, NoteEntity> roots, Map<Long, NoteAuthor> members) {
    if (roots.isEmpty()) {
      return Set.of();
    }
    NoteAuthor viewer = members.get(viewerId);
    ReplyGate.Member replier =
        new ReplyGate.Member(viewerId, viewer == null ? null : viewer.username());
    List<NoteEntity> closed = new ArrayList<>();
    for (NoteEntity root : roots.values()) {
      if (root.getReplyPolicy().limited() && !ReplyGate.admits(root, replier, () -> false)) {
        closed.add(root);
      }
    }
    Set<Long> writers =
        closed.stream()
            .filter(root -> root.getReplyPolicy() == NoteReplyPolicy.FOLLOWING)
            .map(NoteEntity::getUserId)
            .collect(Collectors.toSet());
    Set<Long> following = writers.isEmpty() ? Set.of() : people.followersOf(viewerId, writers);
    return closed.stream()
        .filter(
            root -> !ReplyGate.admits(root, replier, () -> following.contains(root.getUserId())))
        .map(NoteEntity::getId)
        .collect(Collectors.toSet());
  }

  public List<ProfileRepliesView.Item> ofReplies(List<NoteEntity> page, Long viewerId) {
    List<NoteView> replies = of(page, viewerId);
    Set<Long> parentIds =
        page.stream()
            .map(NoteEntity::getInReplyToId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    List<NoteEntity> heard = pages.heardAmong(parentIds, viewerId);
    Set<Long> allowed = access.readable(heard, viewerId);
    List<NoteEntity> readable =
        heard.stream().filter(parent -> allowed.contains(parent.getId())).toList();
    Set<Long> authorIds = new HashSet<>();
    Set<Long> remoteIds = new HashSet<>();
    readable.forEach(parent -> collectAuthor(parent, authorIds, remoteIds));
    Authors authors =
        new Authors(people.activeAuthors(authorIds), people.remoteAuthors(remoteIds, viewerId));
    Map<Long, ProfileRepliesView.ReplyContext> contexts = new HashMap<>();
    for (NoteEntity parent : readable) {
      NoteAuthor author = authors.of(parent);
      if (author != null) {
        contexts.put(
            parent.getId(),
            new ProfileRepliesView.ReplyContext(
                parent.getId(),
                author,
                parent.getContentWarning() == null ? parent.excerpt() : null,
                parent.getContentWarning()));
      }
    }
    return replies.stream()
        .map(
            reply ->
                new ProfileRepliesView.Item(
                    reply, reply.inReplyToId() == null ? null : contexts.get(reply.inReplyToId())))
        .toList();
  }

  public List<ProfileMediaView.Item> ofMedia(List<NoteEntity> page) {
    Map<Long, List<NoteView.Media>> images = images(page.stream().map(NoteEntity::getId).toList());
    List<ProfileMediaView.Item> items = new ArrayList<>(page.size());
    for (NoteEntity note : page) {
      List<NoteView.Media> attached = images.getOrDefault(note.getId(), List.of());
      if (!attached.isEmpty()) {
        items.add(
            new ProfileMediaView.Item(
                note.getId(),
                note.getCreatedAt(),
                attached.getFirst(),
                attached.size(),
                note.isSensitive(),
                note.getContentWarning()));
      }
    }
    return items;
  }

  // A feed page: each top-level note that the author went on in their own replies carries the next
  // part, so the feed can show the first two parts joined. One query walks every chain on the page,
  // and the next parts are viewed in the same batch as the page.
  public List<NoteView> ofFeed(List<NoteEntity> page, Long viewerId) {
    List<Long> roots = page.stream().filter(NoteEntity::isTopLevel).map(NoteEntity::getId).toList();
    Map<Long, List<Long>> chains =
        roots.isEmpty()
            ? Map.of()
            : chains(pages.selfReplies(roots, NoteEntity.MAX_THREAD_NOTES - 1));
    if (chains.isEmpty()) {
      return of(page, viewerId);
    }
    List<NoteEntity> batch = new ArrayList<>(page);
    batch.addAll(notes.findAllByIdIn(chains.values().stream().map(List::getFirst).toList()));
    Map<Long, NoteView> byId = new HashMap<>();
    of(batch, viewerId).forEach(view -> byId.putIfAbsent(view.id(), view));
    List<NoteView> views = new ArrayList<>(page.size());
    for (NoteEntity note : page) {
      NoteView view = byId.get(note.getId());
      if (view == null) {
        continue;
      }
      List<Long> chain = chains.get(note.getId());
      NoteView next = chain == null ? null : byId.get(chain.getFirst());
      views.add(
          next == null
              ? view
              : view.withThread(new NoteView.SelfThread(chain.size() + 1, List.of(next))));
    }
    return views;
  }

  // Each root's parts in order. When the author answered the same part twice, the earlier answer
  // carries the thread on, as on Threads and X.
  static Map<Long, List<Long>> chains(List<SelfReply> replies) {
    Map<Long, Map<Long, Long>> firstAnswer = new HashMap<>();
    for (SelfReply reply : replies) {
      firstAnswer
          .computeIfAbsent(reply.rootId(), root -> new HashMap<>())
          .merge(reply.parentId(), reply.id(), Math::min);
    }
    Map<Long, List<Long>> chains = new HashMap<>();
    firstAnswer.forEach(
        (root, answers) -> {
          List<Long> chain = new ArrayList<>();
          for (Long next = answers.get(root); next != null; next = answers.get(next)) {
            chain.add(next);
          }
          if (!chain.isEmpty()) {
            chains.put(root, chain);
          }
        });
    return chains;
  }

  // Like link cards, a page without a poll of its own costs no query: a poll from elsewhere carries
  // its server's counts.
  private Map<Long, NoteView.Poll> polls(List<NoteEntity> visible, Long viewerId) {
    List<NoteEntity> polled = visible.stream().filter(NoteEntity::hasPoll).toList();
    Map<Long, NoteView.Poll> views = new HashMap<>();
    if (polled.isEmpty()) {
      return views;
    }
    List<Long> counted =
        polled.stream().filter(note -> !note.isRemote()).map(NoteEntity::getId).toList();
    Map<Long, NotePollTally> tallies =
        counted.isEmpty() ? Map.of() : polls.tallies(counted, viewerId);
    Instant now = clock.instant();
    for (NoteEntity note : polled) {
      NotePollTally tally =
          note.isRemote()
              ? note.remoteTally()
              : tallies.getOrDefault(note.getId(), NotePollTally.NONE);
      views.put(note.getId(), NotePolls.view(note, tally, viewerId, now));
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

  private static NoteView.QuotedNote quotedNote(NoteEntity quoted, NoteBatch batch) {
    if (quoted == null) {
      return null;
    }
    return new NoteView.QuotedNote(
        quoted.getId(),
        quoted.getBody(),
        quoted.getCreatedAt(),
        batch.authorOf(quoted),
        batch.images().getOrDefault(quoted.getId(), List.of()),
        quoted.getContentWarning(),
        quoted.isSensitive());
  }

  private Map<Long, List<NoteView.Media>> images(List<Long> noteIds) {
    Map<Long, List<NoteView.Media>> byNote = new LinkedHashMap<>();
    for (NoteMediaEntity image : media.findByNoteIds(noteIds)) {
      byNote
          .computeIfAbsent(image.getNoteId(), id -> new ArrayList<>())
          .add(NoteView.Media.of(image));
    }
    return byNote;
  }
}
