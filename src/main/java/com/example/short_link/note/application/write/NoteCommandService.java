package com.example.short_link.note.application.write;

import com.example.short_link.common.collection.CollectionConnectionCleaner;
import com.example.short_link.common.event.NoteDeletedEvent;
import com.example.short_link.common.event.NoteEditedEvent;
import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NoteRepostedEvent;
import com.example.short_link.common.event.NoteRevisedEvent;
import com.example.short_link.common.event.NoteUnrepostedEvent;
import com.example.short_link.common.event.RemoteNoteLikedEvent;
import com.example.short_link.common.note.Hashtags;
import com.example.short_link.common.note.Mentions;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.note.NoteVisibility.Reader;
import com.example.short_link.common.post.SeriesItemCleaner;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.note.application.access.NoteAccess;
import com.example.short_link.note.application.read.NoteBatch;
import com.example.short_link.note.application.read.NotePolls;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.read.NoteViews;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteLinks;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.NotePollTally;
import com.example.short_link.note.domain.NoteReplyPolicy;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteVersion;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.ReplyGate;
import com.example.short_link.note.domain.repository.NoteBookmarkRepository;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePageReader;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
import com.example.short_link.note.domain.repository.NoteRoomReader;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import com.example.short_link.note.domain.repository.RoomNoteErasureRepository;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class NoteCommandService {

  private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}");

  private final NoteRepository notes;
  private final NotePageReader pages;
  private final RoomNoteErasureRepository erasures;
  private final NoteLikeRepository likes;
  private final NoteRepostRepository reposts;
  private final NoteBookmarkRepository bookmarks;
  private final NoteMediaRepository media;
  private final QuotedPostReader quotedPosts;
  private final NotePeopleReader people;
  private final NoteImages images;
  private final NoteViews views;
  private final NoteAccess access;
  private final NoteRoomReader rooms;
  private final UserModerationGuard moderation;
  private final UserBlockChecker blocks;
  private final CollectionConnectionCleaner connections;
  private final SeriesItemCleaner seriesItems;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transaction;
  private final Clock clock;

  private record Checked(
      String body,
      List<NoteImages.StoredImage> stored,
      List<String> pollOptions,
      NoteVisibility requested,
      NoteEntity parent,
      NoteEntity quotedNote,
      NoteAuthor quotedAuthor,
      QuotedPost quoted,
      List<String> handles,
      Map<Long, NoteAuthor> authors,
      String language,
      NoteReplyPolicy replyPolicy,
      Long roomId) {}

  void validate(Long userId, NoteDraft draft, List<NoteImages.StoredImage> stored) {
    check(userId, draft, stored);
  }

  private Checked check(Long userId, NoteDraft draft, List<NoteImages.StoredImage> stored) {
    moderation.requireCanWrite(userId);
    String body = normalize(draft.body());
    List<NoteDraft.Image> attached = draft.images() == null ? List.of() : draft.images();
    requireContent(body, !attached.isEmpty());
    List<String> pollOptions = draft.poll() == null ? null : pollOptions(draft.poll());
    if (pollOptions != null && !attached.isEmpty()) {
      throw new NoteException(NoteErrorCode.NOTE_POLL_WITH_MEDIA);
    }
    NoteVisibility requested =
        draft.visibility() == null
            ? null
            : NoteVisibility.parse(draft.visibility())
                .orElseThrow(
                    () ->
                        new NoteException(
                            NoteErrorCode.NOTE_VISIBILITY_INVALID, draft.visibility()));
    NoteEntity parent = null;
    if (draft.inReplyToId() != null) {
      parent = find(draft.inReplyToId());
      requireReadable(userId, parent);
      requireNoBlockBetween(userId, parent, NoteErrorCode.NOTE_REPLY_BLOCKED);
    }
    if (draft.quotedPostId() != null && draft.quotedNoteId() != null) {
      throw new NoteException(NoteErrorCode.NOTE_QUOTE_CONFLICT);
    }
    NoteEntity quotedNote = null;
    if (draft.quotedNoteId() != null) {
      quotedNote =
          notes
              .findById(draft.quotedNoteId())
              .orElseThrow(
                  () ->
                      new NoteException(
                          NoteErrorCode.NOTE_QUOTED_NOTE_NOT_FOUND, draft.quotedNoteId()));
      requireReadable(userId, quotedNote);
      if (!quotedNote.getVisibility().shareable()) {
        throw new NoteException(NoteErrorCode.NOTE_NOT_SHAREABLE);
      }
      requireNoBlockBetween(userId, quotedNote, NoteErrorCode.NOTE_INTERACTION_BLOCKED);
    }
    QuotedPost quoted = null;
    if (draft.quotedPostId() != null) {
      quoted = quotedPosts.publishedByIds(Set.of(draft.quotedPostId())).get(draft.quotedPostId());
      if (quoted == null) {
        throw new NoteException(NoteErrorCode.NOTE_QUOTE_NOT_FOUND, draft.quotedPostId());
      }
    }
    Set<Long> authorIds = new HashSet<>(Set.of(userId));
    if (quotedNote != null && !quotedNote.isRemote()) {
      authorIds.add(quotedNote.getUserId());
    }
    List<String> handles = Mentions.of(body);
    Map<Long, NoteAuthor> authors =
        handles.isEmpty()
            ? people.activeAuthors(authorIds)
            : people.activeAuthors(authorIds, handles);
    NoteAuthor quotedAuthor = null;
    if (quotedNote != null) {
      quotedAuthor =
          quotedNote.isRemote()
              ? people
                  .remoteAuthors(Set.of(quotedNote.getRemoteActorId()), userId)
                  .get(quotedNote.getRemoteActorId())
              : authors.get(quotedNote.getUserId());
      if (quotedAuthor == null) {
        throw new NoteException(NoteErrorCode.NOTE_QUOTED_NOTE_NOT_FOUND, draft.quotedNoteId());
      }
    }
    if (parent != null) {
      requireMayAnswer(userId, parent, authors.get(userId));
    }
    Long roomId = room(userId, draft.roomId(), requested, parent);

    return new Checked(
        body,
        stored,
        pollOptions,
        requested,
        parent,
        quotedNote,
        quotedAuthor,
        quoted,
        handles,
        authors,
        language(draft.language()),
        replyPolicy(draft.replyPolicy()),
        roomId);
  }

  // A reply stays in its parent's room; a room note is posted by a member and is a room note only.
  private Long room(Long userId, Long asked, NoteVisibility requested, NoteEntity parent) {
    Long roomId = parent != null ? parent.getRoomId() : asked;
    if (asked != null && !asked.equals(roomId)) {
      throw new NoteException(NoteErrorCode.NOTE_ROOM_MISMATCH);
    }
    if (requested != null && requested.inRoom() != (roomId != null)) {
      throw new NoteException(NoteErrorCode.NOTE_VISIBILITY_INVALID, requested.apiName());
    }
    if (roomId != null && !rooms.isMember(roomId, userId)) {
      throw new NoteException(NoteErrorCode.NOTE_ROOM_NOT_FOUND, roomId);
    }
    return roomId;
  }

  private static NoteReplyPolicy replyPolicy(String raw) {
    return raw == null
        ? null
        : NoteReplyPolicy.parse(raw)
            .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_REPLY_POLICY_INVALID, raw));
  }

  private void requireMayAnswer(Long userId, NoteEntity parent, NoteAuthor replier) {
    if (!parent.getReplyPolicy().limited()) {
      return;
    }
    NoteEntity root =
        parent.conversation().equals(parent.getId())
            ? parent
            : notes.findById(parent.conversation()).orElse(null);
    boolean admitted =
        ReplyGate.admits(
            root,
            new ReplyGate.Member(userId, replier == null ? null : replier.username()),
            () -> !people.followersOf(userId, Set.of(root.getUserId())).isEmpty());
    if (!admitted) {
      throw new NoteException(NoteErrorCode.NOTE_REPLY_RESTRICTED);
    }
  }

  // A thread posts as one: each note answers the one before it, and all of them land or none does.
  public List<NoteView> createThread(Long userId, List<NoteDraft> drafts) {
    if (drafts == null || drafts.size() < 2 || drafts.size() > NoteEntity.MAX_THREAD_NOTES) {
      throw new NoteException(NoteErrorCode.NOTE_THREAD_SIZE, NoteEntity.MAX_THREAD_NOTES);
    }
    List<List<NoteImages.StoredImage>> stored =
        drafts.stream().map(draft -> images.verifyAll(userId, draft.images())).toList();
    return transaction.execute(
        status -> {
          List<NoteView> created = new ArrayList<>(drafts.size());
          NoteView previous = null;
          for (int i = 0; i < drafts.size(); i++) {
            NoteDraft draft = drafts.get(i);
            NoteView note =
                write(
                    userId,
                    previous == null
                        ? draft
                        : draft.continuing(
                            previous.id(), previous.contentWarning(), previous.sensitive()),
                    stored.get(i));
            created.add(note);
            previous = note;
          }
          return created;
        });
  }

  public NoteView create(Long userId, NoteDraft draft) {
    List<NoteImages.StoredImage> stored = images.verifyAll(userId, draft.images());
    return transaction.execute(status -> write(userId, draft, stored));
  }

  NoteView write(Long userId, NoteDraft draft, List<NoteImages.StoredImage> verified) {
    Checked checked = check(userId, draft, verified);
    String body = checked.body();
    List<NoteImages.StoredImage> stored = checked.stored();
    List<String> pollOptions = checked.pollOptions();
    NoteVisibility requested = checked.requested();
    NoteEntity parent = checked.parent();
    Long parentId = parent == null ? null : parent.getId();
    NoteEntity quotedNote = checked.quotedNote();
    QuotedPost quoted = checked.quoted();
    List<String> handles = checked.handles();
    Map<Long, NoteAuthor> authors = checked.authors();
    NoteEntity fresh =
        new NoteEntity(userId, body, parentId, draft.quotedPostId(), draft.quotedNoteId());
    if (parent != null) {
      fresh.answer(parent);
    } else if (checked.replyPolicy() != null) {
      fresh.limitReplies(checked.replyPolicy());
    }
    fresh.markContent(warning(draft.contentWarning()), draft.sensitive());
    fresh.writeIn(checked.language());
    fresh.showTo(
        requested != null
            ? requested
            : parent != null ? parent.getVisibility() : NoteVisibility.DEFAULT);
    if (checked.roomId() != null) {
      fresh.postIn(checked.roomId());
    }
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    if (pollOptions != null) {
      fresh.attachPoll(
          pollOptions, now.plusSeconds(draft.poll().expiresIn()), draft.poll().multiple());
    }
    NoteEntity note = notes.save(fresh);
    List<NoteMediaEntity> rows = new ArrayList<>(stored.size());
    for (int i = 0; i < stored.size(); i++) {
      NoteImages.StoredImage image = stored.get(i);
      rows.add(
          new NoteMediaEntity(
              note.getId(),
              i,
              image.key(),
              image.url(),
              image.contentType(),
              image.altText(),
              image.width(),
              image.height()));
    }
    media.saveAll(rows);
    notes.tag(note.getId(), Hashtags.of(body));
    List<NoteAuthor> mentioned = members(authors, handles);
    if (note.getVisibility().namesReaders()) {
      Set<Long> recipients = new LinkedHashSet<>(recipients(mentioned, userId));
      if (parent != null && parent.getUserId() != null && !parent.getUserId().equals(userId)) {
        recipients.add(parent.getUserId());
      }
      notes.addRecipients(note.getId(), recipients);
    }
    Set<Long> readers = unnamedReaders(note, mentioned, parent);
    boolean quoteSeen =
        quotedNote != null
            && !quotedNote.isRemote()
            && access.readable(note, quotedNote.getUserId());
    NotePublication.events(
            new NotePublication.Posted(
                note,
                userId,
                parent,
                quotedNote,
                quoted,
                mentioned,
                readers,
                quoteSeen,
                rows.stream().map(NoteMediaEntity::getStorageKey).toList()))
        .forEach(events::publishEvent);
    Map<Long, List<NoteView.Media>> images = new HashMap<>();
    images.put(note.getId(), rows.stream().map(NoteView.Media::of).toList());
    if (quotedNote != null) {
      images.put(
          quotedNote.getId(),
          media.findByNoteIds(List.of(quotedNote.getId())).stream()
              .map(NoteView.Media::of)
              .toList());
    }
    return NoteViews.assemble(
        note,
        NoteBatch.justWritten(
            note,
            authors,
            quotedNote,
            checked.quotedAuthor(),
            quoted,
            images,
            note.hasPoll() ? NotePolls.view(note, NotePollTally.NONE, userId, now) : null,
            note.getRoomId() == null ? Map.of() : rooms.names(Set.of(note.getRoomId()))),
        userId);
  }

  static String language(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String code = raw.strip().toLowerCase(Locale.ROOT);
    if (!LANGUAGE.matcher(code).matches()) {
      throw new NoteException(NoteErrorCode.NOTE_LANGUAGE_INVALID, raw);
    }
    return code;
  }

  private static List<String> pollOptions(NoteDraft.Poll poll) {
    List<String> options =
        (poll.options() == null ? List.<String>of() : poll.options())
            .stream()
                .map(option -> option == null ? "" : option.strip().replaceAll("\\s+", " "))
                .toList();
    boolean valid =
        options.size() >= NoteEntity.MIN_POLL_OPTIONS
            && options.size() <= NoteEntity.MAX_POLL_OPTIONS
            && options.stream()
                .allMatch(
                    option ->
                        !option.isEmpty()
                            && option.codePointCount(0, option.length())
                                <= NoteEntity.MAX_POLL_OPTION_LENGTH)
            && new HashSet<>(options).size() == options.size()
            && poll.expiresIn() != null
            && poll.expiresIn() >= NoteEntity.MIN_POLL_SECONDS
            && poll.expiresIn() <= NoteEntity.MAX_POLL_SECONDS;
    if (!valid) {
      throw new NoteException(NoteErrorCode.NOTE_POLL_INVALID);
    }
    return options;
  }

  // Where naming someone does not make them a reader (a room), who of the named and the answered
  // may read the note; null where it does, since they all may.
  private Set<Long> unnamedReaders(NoteEntity note, List<NoteAuthor> named, NoteEntity parent) {
    if (note.getVisibility().readBy(Reader.NAMED)) {
      return null;
    }
    Set<Long> candidates = new HashSet<>();
    named.forEach(member -> candidates.add(member.id()));
    if (parent != null && parent.getUserId() != null) {
      candidates.add(parent.getUserId());
    }
    return access.readersAmong(note, candidates);
  }

  private static List<Long> recipients(List<NoteAuthor> mentioned, Long authorId) {
    return mentioned.stream().map(NoteAuthor::id).filter(id -> !id.equals(authorId)).toList();
  }

  private static List<NoteAuthor> members(Map<Long, NoteAuthor> found, List<String> handles) {
    Map<String, NoteAuthor> byName = new HashMap<>();
    found.values().forEach(author -> byName.put(author.username(), author));
    return handles.stream().map(byName::get).filter(Objects::nonNull).toList();
  }

  @Transactional
  public NoteView edit(
      Long userId, Long noteId, String rawBody, String rawWarning, Boolean sensitive) {
    moderation.requireCanWrite(userId);
    NoteEntity note = owned(userId, noteId);
    String body = normalize(rawBody);
    boolean hasMedia = !media.findByNoteIds(List.of(noteId)).isEmpty();
    requireContent(body, hasMedia);
    String nextWarning = rawWarning == null ? note.getContentWarning() : warning(rawWarning);
    boolean nextSensitive =
        (sensitive == null ? note.isSensitive() : sensitive) || nextWarning != null;
    if (body.equals(note.getBody())
        && Objects.equals(nextWarning, note.getContentWarning())
        && nextSensitive == note.isSensitive()) {
      return views.of(List.of(note), userId).getFirst();
    }
    boolean hasQuote = note.getQuotedPostId() != null || note.getQuotedNoteId() != null;
    String before = NoteLinks.previewUrl(note.getBody(), hasMedia || note.hasPoll(), hasQuote);
    if (!Hashtags.sameTags(note.getBody(), body)) {
      notes.retag(noteId, Hashtags.of(body));
    }
    List<String> added = new ArrayList<>(Mentions.of(body));
    added.removeAll(Mentions.of(note.getBody()));
    notes.recordVersion(
        noteId,
        new NoteVersion(
            note.getBody(),
            note.getContentWarning(),
            note.isSensitive(),
            note.getEditedAt() == null ? note.getCreatedAt() : note.getEditedAt()));
    note.edit(body, clock.instant().truncatedTo(ChronoUnit.MICROS));
    note.markContent(nextWarning, nextSensitive);
    if (!added.isEmpty()) {
      List<NoteAuthor> newlyMentioned = members(people.activeAuthors(List.of(), added), added);
      if (note.getVisibility().namesReaders()) {
        notes.addRecipients(noteId, recipients(newlyMentioned, userId));
      }
      NotePublication.mentions(
              note,
              userId,
              NotePublication.readersOf(newlyMentioned, unnamedReaders(note, newlyMentioned, null)),
              Set.of(userId))
          .forEach(events::publishEvent);
    }
    events.publishEvent(
        new NoteEditedEvent(
            noteId,
            userId,
            note.getInReplyToId() != null
                || note.getQuotedNoteId() != null
                || !Mentions.remote(body).isEmpty()));
    events.publishEvent(new NoteRevisedEvent(noteId, userId, null, note.excerpt()));
    String after = NoteLinks.previewUrl(body, hasMedia || note.hasPoll(), hasQuote);
    if (!Objects.equals(before, after)) {
      events.publishEvent(new NoteLinkPreviewRequested(noteId, after));
    }
    return views.of(List.of(note), userId).getFirst();
  }

  // As on Mastodon: only the author pins, only their own top-level notes, at most five; the
  // newest pin shows first.
  @Transactional
  public PinStatus setPin(Long userId, Long noteId, boolean on) {
    NoteEntity note = owned(userId, noteId);
    if (!on) {
      note.unpin();
      return new PinStatus(false);
    }
    if (note.isPinned()) {
      return new PinStatus(true);
    }
    if (!note.isTopLevel()) {
      throw new NoteException(NoteErrorCode.NOTE_PIN_REPLY);
    }
    if (note.getVisibility().direct()) {
      throw new NoteException(NoteErrorCode.NOTE_PIN_DIRECT);
    }
    if (notes.countPinned(userId) >= NoteEntity.MAX_PINS) {
      throw new NoteException(NoteErrorCode.NOTE_PIN_LIMIT, NoteEntity.MAX_PINS);
    }
    note.pin(clock.instant().truncatedTo(ChronoUnit.MICROS));
    return new PinStatus(true);
  }

  // The writer deletes their own note. The writer of a thread also removes someone else's reply in
  // it, as a post's owner removes comments: a member's reply is deleted, one from elsewhere is only
  // unhooked here, since its server keeps it.
  @Transactional
  public void delete(Long userId, Long noteId) {
    NoteEntity note = find(noteId);
    if (note.isOwnedBy(userId)) {
      remove(note);
      return;
    }
    if (note.getInReplyToId() == null || !writesThreadOf(userId, note)) {
      throw new NoteException(NoteErrorCode.NOTE_PERMISSION_DENIED);
    }
    if (note.isRemote()) {
      note.detach();
    } else {
      remove(note);
    }
  }

  // As Threads' hidden replies: the thread's writer moves a reply out of the thread, where anyone
  // who can read the thread still finds it under its hidden replies. The replier is not told.
  @Transactional
  public ReplyHiddenStatus setReplyHidden(Long userId, Long noteId, boolean on) {
    NoteEntity reply = find(noteId);
    if (reply.getInReplyToId() == null) {
      throw new NoteException(NoteErrorCode.NOTE_NOT_A_REPLY);
    }
    if (!writesThreadOf(userId, reply)) {
      throw new NoteException(NoteErrorCode.NOTE_PERMISSION_DENIED);
    }
    if (on) {
      reply.hideReply(clock.instant().truncatedTo(ChronoUnit.MICROS));
    } else {
      reply.showReply();
    }
    return new ReplyHiddenStatus(on);
  }

  @Transactional
  public ReplyPolicyStatus setReplyPolicy(Long userId, Long noteId, String raw) {
    NoteReplyPolicy policy =
        NoteReplyPolicy.parse(raw)
            .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_REPLY_POLICY_INVALID, raw));
    NoteEntity note = owned(userId, noteId);
    if (!note.isTopLevel()) {
      throw new NoteException(NoteErrorCode.NOTE_REPLY_POLICY_ON_REPLY);
    }
    if (note.getReplyPolicy() != policy) {
      notes.applyReplyPolicy(noteId, policy);
    }
    return new ReplyPolicyStatus(policy.apiName());
  }

  private boolean writesThreadOf(Long userId, NoteEntity reply) {
    return reply.threadWrittenBy(userId, notes::findById);
  }

  // An admin takes a reported note down as if its author deleted it: a member's note also leaves
  // their followers' servers; a note from elsewhere only leaves this one.
  @Transactional
  public void takeDown(Long noteId) {
    notes.findById(noteId).ifPresent(this::remove);
  }

  private void remove(NoteEntity note) {
    Long noteId = note.getId();
    List<String> keys =
        media.findByNoteIds(List.of(noteId)).stream()
            .map(NoteMediaEntity::getStorageKey)
            .filter(key -> !key.isEmpty())
            .toList();
    likes.deleteAllByNoteId(noteId);
    connections.purgeForNote(noteId);
    seriesItems.purgeForNote(noteId);
    notes.delete(note);
    if (!note.isRemote()) {
      events.publishEvent(
          new NoteDeletedEvent(
              noteId,
              note.getUserId(),
              keys,
              note.getInReplyToId(),
              Mentions.remote(note.getBody()),
              note.getQuotedNoteId(),
              note.getVisibility().federates()));
    }
  }

  @Transactional
  public void requestRoomNoteErasure(Long userId, Long roomId) {
    erasures.requestRoomErasure(userId, roomId, clock.instant().truncatedTo(ChronoUnit.MICROS));
  }

  // One chunk of a requested erasure, each note through the usual delete; the request is done once
  // a chunk comes back short. Running it again after a crash only finds what is left.
  @Transactional
  public int eraseRoomNotes(Long userId, Long roomId, int chunk) {
    List<Long> ids = erasures.roomNoteIdsBy(userId, roomId, chunk);
    notes.findAllByIdIn(ids).forEach(this::remove);
    if (ids.size() < chunk) {
      erasures.finishRoomErasure(userId, roomId);
    }
    return ids.size();
  }

  @Transactional
  public LikeStatus setLike(Long userId, Long noteId, boolean on) {
    NoteEntity note = readable(userId, noteId);
    if (on) {
      if (likes.addIfAbsent(noteId, userId)) {
        events.publishEvent(interaction(NoteInteractionEvent.Type.LIKE, note, userId, null));
        if (note.isRemote()) {
          events.publishEvent(new RemoteNoteLikedEvent(noteId, userId, true));
        }
      }
    } else {
      likes.delete(noteId, userId);
      if (note.isRemote()) {
        events.publishEvent(new RemoteNoteLikedEvent(noteId, userId, false));
      }
    }
    return new LikeStatus(on, statsOf(noteId).likes());
  }

  @Transactional
  public RepostStatus setRepost(Long userId, Long noteId, boolean on) {
    NoteEntity note = readable(userId, noteId);
    if (on) {
      if (!note.getVisibility().shareable()) {
        throw new NoteException(NoteErrorCode.NOTE_NOT_SHAREABLE);
      }
      moderation.requireCanWrite(userId);
      requireNoBlockBetween(userId, note, NoteErrorCode.NOTE_INTERACTION_BLOCKED);
      reposts
          .addIfAbsent(noteId, userId)
          .ifPresent(
              repost -> {
                events.publishEvent(
                    new NoteRepostedEvent(repost.getId(), noteId, userId, note.isRemote()));
                events.publishEvent(
                    interaction(NoteInteractionEvent.Type.REPOST, note, userId, null));
              });
    } else {
      reposts
          .delete(noteId, userId)
          .ifPresent(
              repost ->
                  events.publishEvent(
                      new NoteUnrepostedEvent(repost.getId(), noteId, userId, note.isRemote())));
    }
    return new RepostStatus(on, statsOf(noteId).reposts());
  }

  // Mastodon's "mute conversation": the reader hears nothing more from the thread this note is in.
  @Transactional
  public ConversationMuteStatus setConversationMuted(Long userId, Long noteId, boolean on) {
    NoteEntity note = readable(userId, noteId);
    if (on) {
      notes.muteConversation(userId, note.conversation(), clock.instant());
    } else {
      notes.unmuteConversation(userId, note.conversation());
    }
    return new ConversationMuteStatus(on);
  }

  // A bookmark is the reader's own: it notifies no one and does not federate.
  @Transactional
  public BookmarkStatus setBookmark(Long userId, Long noteId, boolean on) {
    readable(userId, noteId);
    if (on) {
      bookmarks.addIfAbsent(noteId, userId);
    } else {
      bookmarks.delete(noteId, userId);
    }
    return new BookmarkStatus(on);
  }

  private NoteStats statsOf(Long noteId) {
    return pages.stats(List.of(noteId)).getOrDefault(noteId, NoteStats.NONE);
  }

  static NoteInteractionEvent interaction(
      NoteInteractionEvent.Type type, NoteEntity target, Long actorId, NoteEntity source) {
    return new NoteInteractionEvent(
        type,
        target.getUserId(),
        actorId,
        null,
        target.getId(),
        target.excerpt(),
        source == null ? null : source.getId(),
        source == null ? null : source.excerpt(),
        target.conversation());
  }

  private void requireNoBlockBetween(Long userId, NoteEntity note, NoteErrorCode code) {
    if (note.isRemote()) {
      return;
    }
    if (blocks.isBlocked(note.getUserId(), userId) || blocks.isBlocked(userId, note.getUserId())) {
      throw new NoteException(code);
    }
  }

  // A followers-only or direct note someone may not read is, to them, a note that does not exist.
  private NoteEntity readable(Long userId, Long noteId) {
    NoteEntity note = find(noteId);
    requireReadable(userId, note);
    return note;
  }

  private void requireReadable(Long userId, NoteEntity note) {
    if (!access.readable(note, userId)) {
      throw new NoteException(NoteErrorCode.NOTE_NOT_FOUND, note.getId());
    }
  }

  private NoteEntity owned(Long userId, Long noteId) {
    NoteEntity note = find(noteId);
    if (!note.isOwnedBy(userId)) {
      throw new NoteException(NoteErrorCode.NOTE_PERMISSION_DENIED);
    }
    return note;
  }

  private NoteEntity find(Long noteId) {
    return notes
        .findById(noteId)
        .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_NOT_FOUND, noteId));
  }

  private static String normalize(String body) {
    return body == null ? "" : body.strip();
  }

  private static String warning(String raw) {
    String warning = raw == null ? "" : raw.strip();
    if (warning.isEmpty()) {
      return null;
    }
    if (warning.codePointCount(0, warning.length()) > NoteEntity.MAX_WARNING_LENGTH) {
      throw new NoteException(NoteErrorCode.NOTE_WARNING_TOO_LONG, NoteEntity.MAX_WARNING_LENGTH);
    }
    return warning;
  }

  private static void requireContent(String body, boolean hasImages) {
    if (body.isEmpty() && !hasImages) {
      throw new NoteException(NoteErrorCode.NOTE_BODY_REQUIRED);
    }
    if (body.codePointCount(0, body.length()) > NoteEntity.MAX_BODY_LENGTH) {
      throw new NoteException(NoteErrorCode.NOTE_BODY_TOO_LONG, NoteEntity.MAX_BODY_LENGTH);
    }
  }

  public record LikeStatus(boolean liked, long likeCount) {}

  public record RepostStatus(boolean reposted, long repostCount) {}

  public record BookmarkStatus(boolean bookmarked) {}

  public record PinStatus(boolean pinned) {}

  public record ReplyHiddenStatus(boolean hidden) {}

  public record ReplyPolicyStatus(String replyPolicy) {}

  public record ConversationMuteStatus(boolean muted) {}
}
