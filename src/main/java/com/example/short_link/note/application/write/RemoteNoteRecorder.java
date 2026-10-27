package com.example.short_link.note.application.write;

import com.example.short_link.common.event.Actor;
import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NoteRevisedEvent;
import com.example.short_link.common.link.LinkPreviewReader;
import com.example.short_link.common.note.Excerpt;
import com.example.short_link.common.note.Hashtags;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.common.note.RemoteNotes.Poll;
import com.example.short_link.common.note.RemoteNotes.Shape;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// A reply to a member's note tells that member; everyone else it names hears of it as a mention.
// Images stay on their server: the row keeps the remote address and no storage key.
@Component
@RequiredArgsConstructor
class RemoteNoteRecorder implements RemoteNotes {

  private static final int MAX_WARNING = NoteEntity.MAX_WARNING_LENGTH;
  private static final int MAX_TITLE = 255;
  private static final int MAX_CONTENT_TYPE = 32;

  private final NoteRepository notes;
  private final RemoteNoteRepository store;
  private final NoteMediaRepository media;
  private final NoteLinkPreviewRepository previews;
  private final NotePeopleReader people;
  private final ApplicationEventPublisher events;
  private final Clock clock;

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
    Long parentId = received.inReplyToLocalId();
    if (parentId == null && received.inReplyToUri() != null) {
      parentId = store.idByUri(received.inReplyToUri()).orElse(null);
    }
    NoteEntity parent = parentId == null ? null : notes.findById(parentId).orElse(null);
    if (parent != null && !mayAnswer(received.remoteActorId(), parent)) {
      parent = null;
    }
    NoteVisibility visibility = received.visibility();
    String warning = warning(received.contentWarning());
    Shape shape = received.shape();
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Poll poll = shape.poll();
    Optional<Long> stored =
        store.insertRemote(
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
                parent == null ? NoteReplyPolicy.EVERYONE : parent.getReplyPolicy(),
                received.language(),
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
                        poll.voters()),
                received.quotedNoteId()));
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
    if (!received.notifies()) {
      return stored;
    }
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
    return stored;
  }

  // A thread whose writer limited replies keeps an answer from elsewhere only from an account the
  // first note names or, under following, one the writer follows; any other is kept unattached.
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

  // As on Mastodon, an Update older than the edit already kept changes nothing, and only a change
  // to the text, warning, sensitive mark or media is an edit that people who shared the note hear
  // of. The row is locked so two Updates of one note apply in order.
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
    if (editedAt != null && note.getEditedAt() != null && note.getEditedAt().isAfter(editedAt)) {
      return true;
    }
    String warning = warning(revision.contentWarning());
    boolean sensitive = revision.sensitive() || warning != null;
    List<NoteMediaEntity> attached = mediaRows(noteId, revision.media());
    boolean mediaChanged = !sameMedia(media.findByNoteIds(List.of(noteId)), attached);
    note.writeIn(revision.language());
    Shape shape = revision.shape();
    String title = cut(shape.title(), MAX_TITLE);
    boolean reshaped =
        !Objects.equals(title, note.getRemoteTitle())
            || !Objects.equals(note.pollOptions(), pollOptions(shape.poll()));
    note.describeElsewhere(shape.kind().name(), title, cut(shape.community(), MAX_TITLE));
    tally(note, shape.poll());
    String body =
        revision.quoteLink() != null && note.getQuotedNoteId() == null
            ? revision.body() + "\n\n" + revision.quoteLink()
            : revision.body();
    if (body.equals(note.getBody())
        && Objects.equals(warning, note.getContentWarning())
        && sensitive == note.isSensitive()
        && !mediaChanged
        && !reshaped) {
      return true;
    }
    boolean retagged = !Hashtags.sameTags(note.getBody(), body);
    note.edit(body, editedAt == null ? clock.instant().truncatedTo(ChronoUnit.MICROS) : editedAt);
    note.markContent(warning, sensitive);
    if (retagged) {
      notes.retag(noteId, Hashtags.of(body));
    }
    if (mediaChanged) {
      media.deleteAllByNoteId(noteId);
      media.saveAll(attached);
    }
    events.publishEvent(
        new NoteRevisedEvent(noteId, Actor.remote(remoteActorId), Excerpt.of(body)));
    return true;
  }

  @Override
  @Transactional
  public boolean retract(Long remoteActorId, String uri) {
    return store.deleteRemote(remoteActorId, uri) > 0;
  }

  @Override
  @Transactional(readOnly = true)
  public int countListed(Long remoteActorId, int atMost) {
    return store.countListed(remoteActorId, atMost);
  }

  // New counts or a later end are not an edit: Mastodon sends them in an Update as votes come in.
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
