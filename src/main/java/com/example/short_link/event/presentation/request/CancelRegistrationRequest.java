package com.example.short_link.event.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelRegistrationRequest(@NotBlank @Size(max = 64) String token) {}
