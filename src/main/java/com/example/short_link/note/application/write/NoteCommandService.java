package com.example.short_link.note.application.write;

import com.example.short_link.common.collection.CollectionConnectionCleaner;
import com.example.short_link.common.event.NoteDeletedEvent;
import com.example.short_link.common.event.NoteEditedEvent;
import com.example.short_link.common.event.NotePublishedEvent;
import com.example.short_link.common.event.NoteRepostedEvent;
import com.example.short_link.common.event.NoteUnrepostedEvent;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.read.NoteViews;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteCommandService {

  private final NoteRepository notes;
  private final NoteLikeRepository likes;
  private final NoteRepostRepository reposts;
  private final NoteMediaRepository media;
  private final QuotedPostReader quotedPosts;
  private final NotePeopleReader people;
  private final NoteImages images;
  private final NoteViews views;
  private final UserModerationGuard moderation;
  private final UserBlockChecker blocks;
  private final CollectionConnectionCleaner connections;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @Autowired
  public NoteCommandService(
      NoteRepository notes,
      NoteLikeRepository likes,
      NoteRepostRepository reposts,
      NoteMediaRepository media,
      QuotedPostReader quotedPosts,
      NotePeopleReader people,
      NoteImages images,
      NoteViews views,
      UserModerationGuard moderation,
      UserBlockChecker blocks,
      CollectionConnectionCleaner connections,
      ApplicationEventPublisher events) {
    this(
        notes,
        likes,
        reposts,
        media,
        quotedPosts,
        people,
        images,
        views,
        moderation,
        blocks,
        connections,
        events,
        Clock.systemUTC());
  }

  NoteCommandService(
      NoteRepository notes,
      NoteLikeRepository likes,
      NoteRepostRepository reposts,
      NoteMediaRepository media,
      QuotedPostReader quotedPosts,
      NotePeopleReader people,
      NoteImages images,
      NoteViews views,
      UserModerationGuard moderation,
      UserBlockChecker blocks,
      CollectionConnectionCleaner connections,
      ApplicationEventPublisher events,
      Clock clock) {
    this.notes = notes;
    this.likes = likes;
    this.reposts = reposts;
    this.media = media;
    this.quotedPosts = quotedPosts;
    this.people = people;
    this.images = images;
    this.views = views;
    this.moderation = moderation;
    this.blocks = blocks;
    this.connections = connections;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public NoteView create(Long userId, NoteDraft draft) {
    moderation.requireCanWrite(userId);
    String body = normalize(draft.body());
    List<NoteDraft.Image> attached = draft.images() == null ? List.of() : draft.images();
    requireContent(body, !attached.isEmpty());
    if (attached.size() > NoteMediaEntity.MAX_PER_NOTE) {
      throw new NoteException(NoteErrorCode.NOTE_TOO_MANY_IMAGES, NoteMediaEntity.MAX_PER_NOTE);
    }
    Long parentId = null;
    if (draft.inReplyToId() != null) {
      NoteEntity parent = find(draft.inReplyToId());
      if (blocks.isBlocked(parent.getUserId(), userId)
          || blocks.isBlocked(userId, parent.getUserId())) {
        throw new NoteException(NoteErrorCode.NOTE_REPLY_BLOCKED);
      }
      parentId = parent.getId();
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
      requireNotBlocked(userId, quotedNote);
    }
    QuotedPost quoted = null;
    if (draft.quotedPostId() != null) {
      quoted = quotedPosts.publishedByIds(Set.of(draft.quotedPostId())).get(draft.quotedPostId());
      if (quoted == null) {
        throw new NoteException(NoteErrorCode.NOTE_QUOTE_NOT_FOUND, draft.quotedPostId());
      }
    }
    List<NoteImages.StoredImage> stored =
        attached.stream().map(image -> images.verify(userId, image)).toList();

    Set<Long> authorIds = new HashSet<>(Set.of(userId));
    if (quotedNote != null) {
      authorIds.add(quotedNote.getUserId());
    }
    Map<Long, NoteAuthor> authors = people.activeAuthors(authorIds);
    if (quotedNote != null && !authors.containsKey(quotedNote.getUserId())) {
      throw new NoteException(NoteErrorCode.NOTE_QUOTED_NOTE_NOT_FOUND, draft.quotedNoteId());
    }

    NoteEntity note =
        notes.save(
            new NoteEntity(userId, body, parentId, draft.quotedPostId(), draft.quotedNoteId()));
    List<NoteMediaEntity> rows = new ArrayList<>(stored.size());
    for (int i = 0; i < stored.size(); i++) {
      NoteImages.StoredImage image = stored.get(i);
      rows.add(
          new NoteMediaEntity(
              note.getId(), i, image.key(), image.url(), image.contentType(), image.altText()));
    }
    media.saveAll(rows);
    events.publishEvent(new NotePublishedEvent(note.getId(), userId));
    return new NoteView(
        note.getId(),
        note.getBody(),
        note.getCreatedAt(),
        null,
        0L,
        false,
        authors.get(userId),
        stored.stream()
            .map(image -> new NoteView.Media(image.url(), image.altText(), image.contentType()))
            .toList(),
        quoted,
        parentId,
        0L,
        0L,
        false,
        quotedNote == null ? null : quotedView(quotedNote, authors.get(quotedNote.getUserId())));
  }

  private NoteView.QuotedNote quotedView(NoteEntity quoted, NoteAuthor author) {
    return new NoteView.QuotedNote(
        quoted.getId(),
        quoted.getBody(),
        quoted.getCreatedAt(),
        author,
        media.findByNoteIds(List.of(quoted.getId())).stream()
            .map(
                image ->
                    new NoteView.Media(image.getUrl(), image.getAltText(), image.getContentType()))
            .toList());
  }

  @Transactional
  public NoteView edit(Long userId, Long noteId, String rawBody) {
    NoteEntity note = owned(userId, noteId);
    String body = normalize(rawBody);
    requireContent(body, !media.findByNoteIds(List.of(noteId)).isEmpty());
    note.edit(body, clock.instant().truncatedTo(ChronoUnit.MICROS));
    events.publishEvent(new NoteEditedEvent(noteId, userId));
    return views.of(List.of(note), userId).getFirst();
  }

  @Transactional
  public void delete(Long userId, Long noteId) {
    NoteEntity note = owned(userId, noteId);
    List<String> keys =
        media.findByNoteIds(List.of(noteId)).stream().map(NoteMediaEntity::getStorageKey).toList();
    likes.deleteAllByNoteId(noteId);
    connections.purgeForNote(noteId);
    notes.delete(note);
    events.publishEvent(new NoteDeletedEvent(noteId, userId, keys));
  }

  @Transactional
  public LikeStatus setLike(Long userId, Long noteId, boolean on) {
    NoteEntity note = find(noteId);
    if (on) {
      likes.addIfAbsent(noteId, userId);
    } else {
      likes.delete(noteId, userId);
    }
    return new LikeStatus(on, note.isOwnedBy(userId) ? likes.countByNoteId(noteId) : 0L);
  }

  @Transactional
  public RepostStatus setRepost(Long userId, Long noteId, boolean on) {
    NoteEntity note = find(noteId);
    if (on) {
      moderation.requireCanWrite(userId);
      requireNotBlocked(userId, note);
      reposts
          .addIfAbsent(noteId, userId)
          .ifPresent(
              repost -> events.publishEvent(new NoteRepostedEvent(repost.getId(), noteId, userId)));
    } else {
      reposts
          .delete(noteId, userId)
          .ifPresent(
              repost ->
                  events.publishEvent(new NoteUnrepostedEvent(repost.getId(), noteId, userId)));
    }
    return new RepostStatus(on, note.isOwnedBy(userId) ? reposts.countByNoteId(noteId) : 0L);
  }

  private void requireNotBlocked(Long userId, NoteEntity note) {
    if (blocks.isBlocked(note.getUserId(), userId) || blocks.isBlocked(userId, note.getUserId())) {
      throw new NoteException(NoteErrorCode.NOTE_INTERACTION_BLOCKED);
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

  private static void requireContent(String body, boolean hasImages) {
    if (body.isEmpty() && !hasImages) {
      throw new NoteException(NoteErrorCode.NOTE_BODY_REQUIRED);
    }
    if (body.codePointCount(0, body.length()) > NoteEntity.MAX_BODY_LENGTH) {
      throw new NoteException(NoteErrorCode.NOTE_BODY_TOO_LONG, NoteEntity.MAX_BODY_LENGTH);
    }
  }

  // likeCount is the real number only for the note's author; others get 0 so older clients that
  // require the field keep decoding.
  public record LikeStatus(boolean liked, long likeCount) {}

  // repostCount is the real number only for the note's author, like likeCount.
  public record RepostStatus(boolean reposted, long repostCount) {}
}
