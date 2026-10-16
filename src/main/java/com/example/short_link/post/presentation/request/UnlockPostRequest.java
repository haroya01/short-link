package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UnlockPostRequest(@NotNull @Size(max = 1000) String password) {}
