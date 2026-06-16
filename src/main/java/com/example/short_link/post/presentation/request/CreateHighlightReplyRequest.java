package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateHighlightReplyRequest(@NotBlank @Size(max = 2000) String body) {}
