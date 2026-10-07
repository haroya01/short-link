package com.example.short_link.common.event;

// A member liked or unliked a note from another server; its server hears of it as Like or Undo.
public record RemoteNoteLikedEvent(Long noteId, Long userId, boolean liked) {}
