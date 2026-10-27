package com.example.short_link.note.domain;

public record NoteStats(long replies, long likes, long reposts, long quotes) {

  public static final NoteStats NONE = new NoteStats(0, 0, 0, 0);
}
