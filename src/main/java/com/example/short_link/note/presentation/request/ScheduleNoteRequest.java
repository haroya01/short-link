package com.example.short_link.note.presentation.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ScheduleNoteRequest(@Valid @NotNull CreateNoteRequest note, Instant scheduledAt) {}
