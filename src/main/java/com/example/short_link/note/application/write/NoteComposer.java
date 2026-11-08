package com.example.short_link.note.application.write;

import com.example.short_link.common.event.Actor;
import com.example.short_link.common.event.NoteEditedEvent;
import com.example.short_link.common.event.NoteRevisedEvent;
import com.example.short_link.common.note.Hashtags;
import com.example.short_link.common.note.Mentions;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.note.NoteVisibility.Reader;
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
import com.example.short_link.note.domain.NoteVersion;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRoomReader;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class NoteComposer {

  private final NoteRepository notes;
  private final NoteMediaRepository media;
  private final NotePeopleReader people;
  private final NoteImages images;
  private final NoteViews views;
  private final NoteAccess access;
  private final NoteRoomReader rooms;
  private final UserModerationGuard moderation;
  private final NoteWriteGuard guard;
  private final NoteDraftCheck drafts;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transaction;
  private final Clock clock;

  void validate(Long userId, NoteDraft draft) {
    drafts.check(userId, draft);
  }

  // All notes of a thread land or none does.
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

  NoteView write(Long userId, NoteDraft draft, List<NoteImages.StoredImage> stored) {
    NoteDraftCheck.Checked checked = drafts.check(userId, draft);
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    NoteEntity note = notes.save(drafted(userId, draft, checked, now));
    List<NoteMediaEntity> rows = mediaRows(note, stored);
    media.saveAll(rows);
    notes.tag(note.getId(), Hashtags.of(checked.text().body()));
    List<NoteAuthor> mentioned = members(checked.authors(), checked.handles());
    if (note.getVisibility().namesReaders()) {
      notes.addRecipients(note.getId(), namedRecipients(mentioned, checked.parent(), userId));
    }
    announce(note, userId, checked, mentioned, rows);
    NoteDraftCheck.Quote quote = checked.quote();
    return NoteViews.assemble(
        note,
        NoteBatch.justWritten(
            note,
            checked.authors(),
            quote.note(),
            quote.author(),
            quote.post(),
            images(note, rows, quote.note()),
            note.hasPoll() ? NotePolls.view(note, NotePollTally.NONE, userId, now) : null,
            note.getRoomId() == null ? Map.of() : rooms.names(Set.of(note.getRoomId()))),
        userId);
  }

  private static NoteEntity drafted(
      Long userId, NoteDraft draft, NoteDraftCheck.Checked checked, Instant now) {
    NoteEntity parent = checked.parent();
    NoteDraftCheck.Audience audience = checked.audience();
    NoteEntity fresh =
        new NoteEntity(
            userId,
            checked.text().body(),
            parent == null ? null : parent.getId(),
            draft.quotedPostId(),
            draft.quotedNoteId());
    if (parent != null) {
      fresh.answer(parent);
    } else if (audience.replyPolicy() != null) {
      fresh.limitReplies(audience.replyPolicy());
    }
    fresh.markContent(warning(draft.contentWarning()), draft.sensitive());
    fresh.writeIn(checked.language());
    fresh.showTo(visibility(audience.requested(), parent));
    if (audience.roomId() != null) {
      fresh.postIn(audience.roomId());
    }
    if (checked.text().pollOptions() != null) {
      fresh.attachPoll(
          checked.text().pollOptions(),
          now.plusSeconds(draft.poll().expiresIn()),
          draft.poll().multiple());
    }
    return fresh;
  }

  private static NoteVisibility visibility(NoteVisibility requested, NoteEntity parent) {
    if (requested != null) {
      return requested;
    }
    return parent != null ? parent.getVisibility() : NoteVisibility.DEFAULT;
  }

  private static List<NoteMediaEntity> mediaRows(
      NoteEntity note, List<NoteImages.StoredImage> stored) {
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
    return rows;
  }

  private static Set<Long> namedRecipients(
      List<NoteAuthor> mentioned, NoteEntity parent, Long userId) {
    Set<Long> recipients = new LinkedHashSet<>(recipients(mentioned, userId));
    if (parent != null && parent.getUserId() != null && !parent.getUserId().equals(userId)) {
      recipients.add(parent.getUserId());
    }
    return recipients;
  }

  private void announce(
      NoteEntity note,
      Long userId,
      NoteDraftCheck.Checked checked,
      List<NoteAuthor> mentioned,
      List<NoteMediaEntity> rows) {
    NoteEntity parent = checked.parent();
    NoteEntity quotedNote = checked.quote().note();
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
                checked.quote().post(),
                mentioned,
                readers,
                quoteSeen,
                rows.stream().map(NoteMediaEntity::getStorageKey).toList()))
        .forEach(events::publishEvent);
  }

  private Map<Long, List<NoteView.Media>> images(
      NoteEntity note, List<NoteMediaEntity> rows, NoteEntity quotedNote) {
    Map<Long, List<NoteView.Media>> images = new HashMap<>();
    images.put(note.getId(), rows.stream().map(NoteView.Media::of).toList());
    if (quotedNote != null) {
      images.put(
          quotedNote.getId(),
          media.findByNoteIds(List.of(quotedNote.getId())).stream()
              .map(NoteView.Media::of)
              .toList());
    }
    return images;
  }

  // null when everyone named or answered may read the note.
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
    NoteEntity note = guard.owned(userId, noteId);
    boolean hasMedia = !media.findByNoteIds(List.of(noteId)).isEmpty();
    String body = NoteDraftCheck.body(rawBody, hasMedia);
    String nextWarning = rawWarning == null ? note.getContentWarning() : warning(rawWarning);
    boolean nextSensitive =
        (sensitive == null ? note.isSensitive() : sensitive) || nextWarning != null;
    if (unchanged(note, body, nextWarning, nextSensitive)) {
      return views.of(List.of(note), userId).getFirst();
    }
    String before = previewUrl(note, note.getBody(), hasMedia);
    if (!Hashtags.sameTags(note.getBody(), body)) {
      notes.retag(noteId, Hashtags.of(body));
    }
    List<String> added = new ArrayList<>(Mentions.of(body));
    added.removeAll(Mentions.of(note.getBody()));
    notes.recordVersion(noteId, NoteVersion.of(note));
    note.edit(body, clock.instant().truncatedTo(ChronoUnit.MICROS));
    note.markContent(nextWarning, nextSensitive);
    if (!added.isEmpty()) {
      tellNewlyMentioned(noteId, note, userId, added);
    }
    events.publishEvent(new NoteEditedEvent(noteId, userId, reachesElsewhere(note, body)));
    events.publishEvent(new NoteRevisedEvent(noteId, Actor.member(userId), note.excerpt()));
    String after = previewUrl(note, body, hasMedia);
    if (!Objects.equals(before, after)) {
      events.publishEvent(new NoteLinkPreviewRequested(noteId, after));
    }
    return views.of(List.of(note), userId).getFirst();
  }

  private static String previewUrl(NoteEntity note, String body, boolean hasMedia) {
    return NoteLinks.previewUrl(
        body,
        hasMedia || note.hasPoll(),
        note.getQuotedPostId() != null || note.getQuotedNoteId() != null);
  }

  private static boolean unchanged(
      NoteEntity note, String body, String warning, boolean sensitive) {
    return body.equals(note.getBody())
        && Objects.equals(warning, note.getContentWarning())
        && sensitive == note.isSensitive();
  }

  private void tellNewlyMentioned(Long noteId, NoteEntity note, Long userId, List<String> added) {
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

  private static boolean reachesElsewhere(NoteEntity note, String body) {
    return note.getInReplyToId() != null
        || note.getQuotedNoteId() != null
        || !Mentions.remote(body).isEmpty();
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
}
