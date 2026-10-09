package com.example.short_link.note.application.write;

import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NoteRevisedEvent;
import com.example.short_link.common.note.Hashtags;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.NoteVisibility;
import com.example.short_link.note.domain.RemoteNoteRow;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// A reply to a member's note tells that member; everyone else it names hears of it as a mention.
// Images stay on their server: the row keeps the remote address and no storage key.
@Component
class RemoteNoteRecorder implements RemoteNotes {

  private static final int MAX_WARNING = NoteEntity.MAX_WARNING_LENGTH;
  private static final int MAX_CONTENT_TYPE = 32;

  private final NoteRepository notes;
  private final NoteMediaRepository media;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @Autowired
  RemoteNoteRecorder(
      NoteRepository notes, NoteMediaRepository media, ApplicationEventPublisher events) {
    this(notes, media, events, Clock.systemUTC());
  }

  RemoteNoteRecorder(
      NoteRepository notes,
      NoteMediaRepository media,
      ApplicationEventPublisher events,
      Clock clock) {
    this.notes = notes;
    this.media = media;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(String uri) {
    return kept(uri).isPresent();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Long> kept(String uri) {
    return uri == null ? Optional.empty() : notes.idByUri(uri);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Target> target(Long noteId) {
    return notes
        .findById(noteId)
        .filter(NoteEntity::isRemote)
        .map(
            note ->
                new Target(
                    note.getUri(), note.getRemoteActorId(), note.getVisibility().shareable()));
  }

  @Override
  @Transactional
  public Optional<Long> receive(Received received) {
    Long parentId = received.inReplyToLocalId();
    if (parentId == null && received.inReplyToUri() != null) {
      parentId = notes.idByUri(received.inReplyToUri()).orElse(null);
    }
    NoteEntity parent = parentId == null ? null : notes.findById(parentId).orElse(null);
    NoteVisibility visibility =
        NoteVisibility.parse(received.visibility()).orElse(NoteVisibility.DIRECT);
    String warning = warning(received.contentWarning());
    Optional<Long> stored =
        notes.insertRemote(
            new RemoteNoteRow(
                received.remoteActorId(),
                received.uri(),
                received.url(),
                received.body(),
                createdAt(received.publishedAt()),
                warning,
                received.sensitive() || warning != null,
                visibility,
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.conversation(),
                parent != null || received.inReplyToUri() != null,
                received.language()));
    if (stored.isEmpty()) {
      return Optional.empty();
    }
    Long noteId = stored.get();
    media.saveAll(mediaRows(noteId, received.media()));
    notes.tag(noteId, Hashtags.of(received.body()));
    Set<Long> addressed = new LinkedHashSet<>(received.addressedUserIds());
    if (visibility.restricted() && !addressed.isEmpty()) {
      notes.addRecipients(noteId, List.copyOf(addressed));
    }
    String excerpt = NoteEntity.excerptOf(received.body());
    if (parent != null && parent.getUserId() != null) {
      events.publishEvent(
          new NoteInteractionEvent(
              NoteInteractionEvent.Type.REPLY,
              parent.getUserId(),
              null,
              received.remoteActorId(),
              parent.getId(),
              parent.excerpt(),
              noteId,
              excerpt,
              parent.conversation()));
      addressed.remove(parent.getUserId());
    }
    for (Long member : addressed) {
      events.publishEvent(
          new NoteInteractionEvent(
              NoteInteractionEvent.Type.MENTION,
              member,
              null,
              received.remoteActorId(),
              noteId,
              excerpt,
              null,
              null,
              parent == null ? noteId : parent.conversation()));
    }
    return stored;
  }

  // As on Mastodon, an Update older than the edit already kept changes nothing, and only a change
  // to the text, warning, sensitive mark or media is an edit that people who shared the note hear
  // of. The row is locked so two Updates of one note apply in order.
  @Override
  @Transactional
  public boolean revise(Long remoteActorId, Long noteId, Revision revision) {
    Optional<NoteEntity> found = notes.findRemoteForUpdate(remoteActorId, noteId);
    if (found.isEmpty()) {
      return false;
    }
    NoteEntity note = found.get();
    Instant editedAt =
        revision.editedAt() == null ? null : revision.editedAt().truncatedTo(ChronoUnit.MICROS);
    if (editedAt != null && note.getEditedAt() != null && note.getEditedAt().isAfter(editedAt)) {
      return true;
    }
    String warning = warning(revision.contentWarning());
    boolean sensitive = revision.sensitive() || warning != null;
    List<NoteMediaEntity> attached = mediaRows(noteId, revision.media());
    boolean mediaChanged = !sameMedia(media.findByNoteIds(List.of(noteId)), attached);
    note.writeIn(revision.language());
    if (revision.body().equals(note.getBody())
        && Objects.equals(warning, note.getContentWarning())
        && sensitive == note.isSensitive()
        && !mediaChanged) {
      return true;
    }
    boolean retagged = !Hashtags.sameTags(note.getBody(), revision.body());
    note.edit(
        revision.body(),
        editedAt == null ? clock.instant().truncatedTo(ChronoUnit.MICROS) : editedAt);
    note.markContent(warning, sensitive);
    if (retagged) {
      notes.retag(noteId, Hashtags.of(revision.body()));
    }
    if (mediaChanged) {
      media.deleteAllByNoteId(noteId);
      media.saveAll(attached);
    }
    events.publishEvent(
        new NoteRevisedEvent(noteId, null, remoteActorId, NoteEntity.excerptOf(revision.body())));
    return true;
  }

  @Override
  @Transactional
  public boolean retract(Long remoteActorId, String uri) {
    return notes.deleteRemote(remoteActorId, uri) > 0;
  }

  private static List<NoteMediaEntity> mediaRows(Long noteId, List<Media> attached) {
    List<NoteMediaEntity> rows = new ArrayList<>();
    if (attached == null) {
      return rows;
    }
    for (Media image : attached) {
      if (rows.size() == NoteMediaEntity.MAX_PER_NOTE) {
        break;
      }
      rows.add(
          new NoteMediaEntity(
              noteId,
              rows.size(),
              "",
              image.url(),
              contentType(image.contentType()),
              cut(image.altText(), NoteMediaEntity.MAX_ALT_TEXT_LENGTH),
              image.width(),
              image.height()));
    }
    return rows;
  }

  private static boolean sameMedia(List<NoteMediaEntity> kept, List<NoteMediaEntity> sent) {
    if (kept.size() != sent.size()) {
      return false;
    }
    for (int i = 0; i < kept.size(); i++) {
      NoteMediaEntity a = kept.get(i);
      NoteMediaEntity b = sent.get(i);
      if (!a.getUrl().equals(b.getUrl())
          || !a.getContentType().equals(b.getContentType())
          || !Objects.equals(a.getAltText(), b.getAltText())
          || !Objects.equals(a.getWidth(), b.getWidth())
          || !Objects.equals(a.getHeight(), b.getHeight())) {
        return false;
      }
    }
    return true;
  }

  // A clock running ahead elsewhere must not pin a note to the top of the feed.
  private Instant createdAt(Instant published) {
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    return published == null || published.isAfter(now)
        ? now
        : published.truncatedTo(ChronoUnit.MICROS);
  }

  private static String contentType(String type) {
    if (type == null || type.isBlank()) {
      return "image/jpeg";
    }
    String stripped = type.strip();
    return stripped.length() <= MAX_CONTENT_TYPE
        ? stripped
        : stripped.substring(0, MAX_CONTENT_TYPE);
  }

  private static String warning(String warning) {
    return cut(warning, MAX_WARNING);
  }

  private static String cut(String value, int max) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String stripped = value.strip();
    return stripped.codePointCount(0, stripped.length()) <= max
        ? stripped
        : stripped.substring(0, stripped.offsetByCodePoints(0, max - 1)) + "…";
  }
}
