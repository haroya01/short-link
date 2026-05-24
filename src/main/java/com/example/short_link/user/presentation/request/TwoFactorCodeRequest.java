package com.example.short_link.user.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorCodeRequest(@NotBlank String code) {}
