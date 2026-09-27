package com.example.short_link.admin.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WarnUserRequest(
    @Size(max = 64) String shortCode, @NotBlank @Size(max = 500) String message) {}
