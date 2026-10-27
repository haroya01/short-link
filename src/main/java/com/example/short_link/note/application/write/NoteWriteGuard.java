package com.example.short_link.note.application.write;

import com.example.short_link.common.user.BlockEdge;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.note.application.access.NoteAccess;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NoteWriteGuard {

  private final NoteRepository notes;
  private final NoteAccess access;
  private final UserBlockChecker blocks;

  void requireNoBlockBetween(Long userId, NoteEntity note, NoteErrorCode code) {
    if (note.isRemote()) {
      return;
    }
    if (blocks.isBlocked(BlockEdge.blocker(note.getUserId()).blocked(userId))
        || blocks.isBlocked(BlockEdge.blocker(userId).blocked(note.getUserId()))) {
      throw new NoteException(code);
    }
  }

  // A followers-only or direct note someone may not read is, to them, a note that does not exist.
  NoteEntity readable(Long userId, Long noteId) {
    NoteEntity note = find(noteId);
    requireReadable(userId, note);
    return note;
  }

  void requireReadable(Long userId, NoteEntity note) {
    if (!access.readable(note, userId)) {
      throw new NoteException(NoteErrorCode.NOTE_NOT_FOUND, note.getId());
    }
  }

  NoteEntity owned(Long userId, Long noteId) {
    NoteEntity note = find(noteId);
    if (!note.isOwnedBy(userId)) {
      throw new NoteException(NoteErrorCode.NOTE_PERMISSION_DENIED);
    }
    return note;
  }

  NoteEntity find(Long noteId) {
    return notes
        .findById(noteId)
        .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_NOT_FOUND, noteId));
  }
}
