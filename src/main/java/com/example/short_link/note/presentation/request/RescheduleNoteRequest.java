package com.example.short_link.note.presentation.request;

import java.time.Instant;

public record RescheduleNoteRequest(Instant scheduledAt) {}
