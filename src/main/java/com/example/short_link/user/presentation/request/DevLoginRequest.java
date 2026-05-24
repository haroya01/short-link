package com.example.short_link.user.presentation.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DevLoginRequest(@NotBlank @Email @Size(max = 255) String email) {}
