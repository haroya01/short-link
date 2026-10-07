package com.example.short_link.common.note;

// The note slice implements this, so resolving a report takes a note down in the same transaction.
public interface NoteModerationPort {

  void takeDown(Long adminUserId, Long noteId);
}
