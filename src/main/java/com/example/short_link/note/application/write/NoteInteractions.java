package com.example.short_link.note.application.write;

import com.example.short_link.common.event.Actor;
import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NoteRepostedEvent;
import com.example.short_link.common.event.NoteUnrepostedEvent;
import com.example.short_link.common.event.RemoteNoteLikedEvent;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.repository.NoteBookmarkRepository;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NotePageReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteInteractions {

  private final NoteWriteGuard guard;
  private final NoteRepository notes;
  private final NotePageReader pages;
  private final NoteLikeRepository likes;
  private final NoteRepostRepository reposts;
  private final NoteBookmarkRepository bookmarks;
  private final UserModerationGuard moderation;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @Transactional
  public LikeStatus setLike(Long userId, Long noteId, boolean on) {
    NoteEntity note = guard.readable(userId, noteId);
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
    NoteEntity note = guard.readable(userId, noteId);
    if (on) {
      if (!note.getVisibility().shareable()) {
        throw new NoteException(NoteErrorCode.NOTE_NOT_SHAREABLE);
      }
      moderation.requireCanWrite(userId);
      guard.requireNoBlockBetween(userId, note, NoteErrorCode.NOTE_INTERACTION_BLOCKED);
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
    NoteEntity note = guard.readable(userId, noteId);
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
    guard.readable(userId, noteId);
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
        Actor.member(actorId),
        target.getId(),
        target.excerpt(),
        source == null ? null : source.getId(),
        source == null ? null : source.excerpt(),
        target.conversation());
  }

  public record LikeStatus(boolean liked, long likeCount) {}

  public record RepostStatus(boolean reposted, long repostCount) {}

  public record BookmarkStatus(boolean bookmarked) {}

  public record ConversationMuteStatus(boolean muted) {}
}
