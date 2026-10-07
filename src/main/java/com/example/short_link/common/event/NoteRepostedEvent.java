package com.example.short_link.common.event;

// remote: the reposted note came from another server, whose author hears of the boost.
public record NoteRepostedEvent(Long repostId, Long noteId, Long userId, boolean remote) {

  public NoteRepostedEvent(Long repostId, Long noteId, Long userId) {
    this(repostId, noteId, userId, false);
  }
}
