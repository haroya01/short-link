package com.example.short_link.note.application.write;

import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NoteRevisedEvent;
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
                received.language()));
    if (stored.isEmpty()) {
      return Optional.empty();
    }
    Long noteId = stored.get();
    saveMedia(noteId, received.media());
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

  @Override
  @Transactional
  public boolean revise(
      Long remoteActorId,
      Long noteId,
      String body,
      String contentWarning,
      boolean sensitive,
      Instant editedAt) {
    String warning = warning(contentWarning);
    boolean revised =
        notes.reviseRemote(
                remoteActorId,
                noteId,
                body,
                warning,
                sensitive || warning != null,
                editedAt == null ? clock.instant() : editedAt)
            > 0;
    if (revised) {
      events.publishEvent(
          new NoteRevisedEvent(noteId, null, remoteActorId, NoteEntity.excerptOf(body)));
    }
    return revised;
  }

  @Override
  @Transactional
  public boolean retract(Long remoteActorId, String uri) {
    return notes.deleteRemote(remoteActorId, uri) > 0;
  }

  private void saveMedia(Long noteId, List<Media> attached) {
    if (attached == null || attached.isEmpty()) {
      return;
    }
    List<NoteMediaEntity> rows = new ArrayList<>();
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
              cut(image.altText(), NoteMediaEntity.MAX_ALT_TEXT_LENGTH)));
    }
    media.saveAll(rows);
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
