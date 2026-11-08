package com.example.short_link.note.application.write;

import static com.example.short_link.note.application.write.RemoteNoteColumns.MAX_TITLE;
import static com.example.short_link.note.application.write.RemoteNoteColumns.cut;
import static com.example.short_link.note.application.write.RemoteNoteColumns.mediaRows;
import static com.example.short_link.note.application.write.RemoteNoteColumns.sameMedia;

import com.example.short_link.common.event.Actor;
import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NoteRevisedEvent;
import com.example.short_link.common.link.LinkPreviewReader;
import com.example.short_link.common.live.LiveRevoke;
import com.example.short_link.common.live.LiveTopic;
import com.example.short_link.common.note.Excerpt;
import com.example.short_link.common.note.Hashtags;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.common.note.RemoteNotes.Poll;
import com.example.short_link.common.note.RemoteNotes.Shape;
import com.example.short_link.note.application.media.RemoteMediaCopies;
import com.example.short_link.note.application.write.RemoteNoteColumns.Marks;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteLinks;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.NoteReplyPolicy;
import com.example.short_link.note.domain.RemoteNoteRow;
import com.example.short_link.note.domain.ReplyGate;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRepository;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.RemoteNoteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class RemoteNoteRecorder implements RemoteNotes {

  private final NoteRepository notes;
  private final RemoteNoteRepository store;
  private final NoteMediaRepository media;
  private final NoteLinkPreviewRepository previews;
  private final NotePeopleReader people;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final RemoteMediaCopies copies;

  @Override
  @Transactional(readOnly = true)
  public boolean exists(String uri) {
    return kept(uri).isPresent();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Long> kept(String uri) {
    return uri == null ? Optional.empty() : store.idByUri(uri);
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
  public void preview(Long noteId, String body, boolean hasMedia, LinkCard card) {
    if (card != null && card.url() != null) {
      previews.put(
          noteId,
          new LinkPreviewReader.Preview(card.url(), card.title(), card.description(), card.image()),
          clock.instant().truncatedTo(ChronoUnit.MICROS));
      return;
    }
    String url = NoteLinks.previewUrl(body, hasMedia, false);
    if (url != null) {
      events.publishEvent(new NoteLinkPreviewRequested(noteId, url));
    }
  }

  @Override
  @Transactional
  public Optional<Long> receive(Received received) {
    NoteEntity parent = answered(received);
    Optional<Long> stored = store.insertRemote(row(received, parent));
    if (stored.isEmpty()) {
      return Optional.empty();
    }
    Long noteId = stored.get();
    copies.request(media.saveAll(mediaRows(noteId, received.media())));
    notes.tag(noteId, Hashtags.of(received.body()));
    Set<Long> addressed = new LinkedHashSet<>(received.addressedUserIds());
    if (received.visibility().restricted() && !addressed.isEmpty()) {
      notes.addRecipients(noteId, List.copyOf(addressed));
    }
    if (received.notifies()) {
      tell(received, noteId, parent, addressed);
    }
    return stored;
  }

  private NoteEntity answered(Received received) {
    Long parentId = received.inReplyToLocalId();
    if (parentId == null && received.inReplyToUri() != null) {
      parentId = store.idByUri(received.inReplyToUri()).orElse(null);
    }
    NoteEntity parent = parentId == null ? null : notes.findById(parentId).orElse(null);
    return parent != null && mayAnswer(received.remoteActorId(), parent) ? parent : null;
  }

  private RemoteNoteRow row(Received received, NoteEntity parent) {
    Marks marks = Marks.of(received.contentWarning(), received.sensitive());
    return new RemoteNoteRow(
        received.remoteActorId(),
        received.uri(),
        received.url(),
        createdAt(received.publishedAt()),
        new RemoteNoteRow.Content(
            received.body(),
            marks.warning(),
            marks.sensitive(),
            received.language(),
            received.emojis()),
        received.visibility(),
        parent == null
            ? new RemoteNoteRow.Conversation(
                null, null, received.inReplyToUri() != null, NoteReplyPolicy.EVERYONE)
            : new RemoteNoteRow.Conversation(
                parent.getId(), parent.conversation(), true, parent.getReplyPolicy()),
        form(received.shape()),
        received.quotedNoteId());
  }

  private RemoteNoteRow.Form form(Shape shape) {
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Poll poll = shape.poll();
    return new RemoteNoteRow.Form(
        shape.kind().name(),
        cut(shape.title(), MAX_TITLE),
        cut(shape.community(), MAX_TITLE),
        poll == null
            ? null
            : new RemoteNoteRow.Poll(
                poll.options(),
                pollEnds(poll, now),
                poll.multiple(),
                now,
                poll.votes(),
                poll.voters()));
  }

  private void tell(Received received, Long noteId, NoteEntity parent, Set<Long> addressed) {
    NoteLiveChanges.of(
            noteId, parent == null ? null : parent.getId(), null, received.visibility().direct())
        .ifPresent(events::publishEvent);
    String excerpt = Excerpt.of(received.body());
    if (parent != null && parent.getUserId() != null) {
      events.publishEvent(
          new NoteInteractionEvent(
              NoteInteractionEvent.Type.REPLY,
              parent.getUserId(),
              Actor.remote(received.remoteActorId()),
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
              Actor.remote(received.remoteActorId()),
              noteId,
              excerpt,
              null,
              null,
              parent == null ? noteId : parent.conversation()));
    }
  }

  private boolean mayAnswer(Long remoteActorId, NoteEntity parent) {
    if (!parent.getReplyPolicy().limited()) {
      return true;
    }
    NoteEntity root =
        parent.conversation().equals(parent.getId())
            ? parent
            : notes.findById(parent.conversation()).orElse(null);
    if (root == null || root.isRemote()) {
      return true;
    }
    NoteAuthor sender = people.remoteAuthors(Set.of(remoteActorId), null).get(remoteActorId);
    return ReplyGate.admits(
        root,
        new ReplyGate.Remote(sender == null ? null : sender.username()),
        () -> people.followsRemote(root.getUserId(), remoteActorId));
  }

  // An Update older than the edit already kept changes nothing. The row is locked so two Updates
  // of one note apply in order.
  @Override
  @Transactional
  public boolean revise(Long remoteActorId, Long noteId, Revision revision) {
    Optional<NoteEntity> found = store.findRemoteForUpdate(remoteActorId, noteId);
    if (found.isEmpty()) {
      return false;
    }
    NoteEntity note = found.get();
    Instant editedAt =
        revision.editedAt() == null ? null : revision.editedAt().truncatedTo(ChronoUnit.MICROS);
    if (outdated(note, editedAt)) {
      return true;
    }
    Marks marks = Marks.of(revision.contentWarning(), revision.sensitive());
    List<NoteMediaEntity> attached = mediaRows(noteId, revision.media());
    boolean mediaChanged = !sameMedia(media.findByNoteIds(List.of(noteId)), attached);
    note.writeIn(revision.language());
    boolean reshaped = reshape(note, revision.shape());
    note.useRemoteEmojis(revision.emojis());
    String body = revisedBody(note, revision);
    if (!mediaChanged && !reshaped && sameText(note, body, marks)) {
      return true;
    }
    rewrite(noteId, note, body, marks, editedAt);
    if (mediaChanged) {
      media.deleteAllByNoteId(noteId);
      copies.request(media.saveAll(attached));
    }
    events.publishEvent(
        new NoteRevisedEvent(noteId, Actor.remote(remoteActorId), Excerpt.of(body)));
    return true;
  }

  private static boolean outdated(NoteEntity note, Instant editedAt) {
    return editedAt != null && note.getEditedAt() != null && note.getEditedAt().isAfter(editedAt);
  }

  private void rewrite(Long noteId, NoteEntity note, String body, Marks marks, Instant editedAt) {
    boolean retagged = !Hashtags.sameTags(note.getBody(), body);
    note.edit(body, editedAt == null ? clock.instant().truncatedTo(ChronoUnit.MICROS) : editedAt);
    note.markContent(marks.warning(), marks.sensitive());
    if (retagged) {
      notes.retag(noteId, Hashtags.of(body));
    }
  }

  private boolean reshape(NoteEntity note, Shape shape) {
    String title = cut(shape.title(), MAX_TITLE);
    boolean reshaped =
        !Objects.equals(title, note.getRemoteTitle())
            || !Objects.equals(note.pollOptions(), pollOptions(shape.poll()));
    note.describeElsewhere(shape.kind().name(), title, cut(shape.community(), MAX_TITLE));
    tally(note, shape.poll());
    return reshaped;
  }

  private static String revisedBody(NoteEntity note, Revision revision) {
    return revision.quoteLink() != null && note.getQuotedNoteId() == null
        ? revision.body() + "\n\n" + revision.quoteLink()
        : revision.body();
  }

  private static boolean sameText(NoteEntity note, String body, Marks marks) {
    return body.equals(note.getBody())
        && Objects.equals(marks.warning(), note.getContentWarning())
        && marks.sensitive() == note.isSensitive();
  }

  @Override
  @Transactional
  public boolean retract(Long remoteActorId, Long noteId, String uri) {
    if (store.deleteRemote(remoteActorId, uri) == 0) {
      return false;
    }
    events.publishEvent(new LiveRevoke.Closed(LiveTopic.note(noteId)));
    return true;
  }

  @Override
  @Transactional(readOnly = true)
  public int countListed(Long remoteActorId, int atMost) {
    return store.countListed(remoteActorId, atMost);
  }

  // Mastodon sends new counts and a later end in an Update as votes come in; they are not an edit.
  private void tally(NoteEntity note, Poll poll) {
    if (poll == null) {
      if (note.hasPoll()) {
        note.dropPoll();
      }
      return;
    }
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    note.pollElsewhere(
        poll.options(), pollEnds(poll, now), poll.multiple(), poll.votes(), poll.voters(), now);
  }

  private static List<String> pollOptions(Poll poll) {
    return poll == null ? List.of() : poll.options();
  }

  private static Instant pollEnds(Poll poll, Instant now) {
    Instant ends = poll.endsAt() == null ? null : poll.endsAt().truncatedTo(ChronoUnit.MICROS);
    if (poll.closed() && (ends == null || ends.isAfter(now))) {
      return now;
    }
    return ends;
  }

  // A clock running ahead elsewhere must not pin a note to the top of the feed.
  private Instant createdAt(Instant published) {
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    return published == null || published.isAfter(now)
        ? now
        : published.truncatedTo(ChronoUnit.MICROS);
  }
}
