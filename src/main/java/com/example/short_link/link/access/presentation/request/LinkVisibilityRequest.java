package com.example.short_link.link.access.presentation.request;

import jakarta.validation.constraints.NotNull;

public record LinkVisibilityRequest(@NotNull Boolean statsPublic) {}
