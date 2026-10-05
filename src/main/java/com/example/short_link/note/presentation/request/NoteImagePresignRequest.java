package com.example.short_link.note.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record NoteImagePresignRequest(@NotBlank String contentType) {}
