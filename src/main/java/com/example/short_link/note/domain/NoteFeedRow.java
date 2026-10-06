package com.example.short_link.note.domain;

// A note in a following feed and, when its newest activity there is a repost, who reposted it.
public record NoteFeedRow(NoteEntity note, Long reposterId) {}
