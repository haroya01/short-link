package com.example.short_link.link.presentation.request;

import jakarta.validation.constraints.NotNull;

public record LinkVisibilityRequest(@NotNull Boolean statsPublic) {}
