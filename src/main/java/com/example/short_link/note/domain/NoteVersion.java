package com.example.short_link.note.domain;

import java.time.Instant;

public record NoteVersion(String body, String contentWarning, boolean sensitive, Instant at) {}
