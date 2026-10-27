package com.example.short_link.note.domain;

// reposterId is set when the note's newest activity in the feed is a repost.
public record NoteFeedRow(NoteEntity note, Long reposterId) {}
