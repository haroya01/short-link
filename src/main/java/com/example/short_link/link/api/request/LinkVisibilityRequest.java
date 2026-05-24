package com.example.short_link.link.api.request;

import jakarta.validation.constraints.NotNull;

public record LinkVisibilityRequest(@NotNull Boolean statsPublic) {}
