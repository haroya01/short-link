package com.example.short_link.user.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record MobileExchangeRequest(@NotBlank String code) {}
