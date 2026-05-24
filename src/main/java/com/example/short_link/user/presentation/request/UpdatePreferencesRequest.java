package com.example.short_link.user.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePreferencesRequest(@NotBlank @Size(max = 64) String timezone) {}
