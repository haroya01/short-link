package com.example.short_link.post.note.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record CreateNoteRequest(@NotBlank String body) {}
