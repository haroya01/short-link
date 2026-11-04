package com.example.short_link.note.application.read;

import java.util.List;

public record NoteRepliesView(
    List<NoteView> items, String next, String latest, boolean continued, long total) {}
