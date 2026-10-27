package com.example.short_link.note.application.write;

import com.example.short_link.common.collection.CollectionConnectionCleaner;
import com.example.short_link.common.event.NoteDeletedEvent;
import com.example.short_link.common.note.Mentions;
import com.example.short_link.common.post.SeriesItemCleaner;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.NoteReplyPolicy;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteThreadControls {

  private final NoteRepository notes;
  private final NoteWriteGuard guard;
  private final NoteMediaRepository media;
  private final NoteLikeRepository likes;
  private final CollectionConnectionCleaner connections;
  private final SeriesItemCleaner seriesItems;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @Transactional
  public PinStatus setPin(Long userId, Long noteId, boolean on) {
    NoteEntity note = guard.owned(userId, noteId);
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

  // A reply from elsewhere that the thread's writer removes is only unhooked here: its server keeps
  // it.
  @Transactional
  public void delete(Long userId, Long noteId) {
    NoteEntity note = guard.find(noteId);
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

  // The replier is not told.
  @Transactional
  public ReplyHiddenStatus setReplyHidden(Long userId, Long noteId, boolean on) {
    NoteEntity reply = guard.find(noteId);
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
    NoteEntity note = guard.owned(userId, noteId);
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

  @Transactional
  public void takeDown(Long noteId) {
    notes.findById(noteId).ifPresent(this::remove);
  }

  void remove(NoteEntity note) {
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

  public record PinStatus(boolean pinned) {}

  public record ReplyHiddenStatus(boolean hidden) {}

  public record ReplyPolicyStatus(String replyPolicy) {}
}
