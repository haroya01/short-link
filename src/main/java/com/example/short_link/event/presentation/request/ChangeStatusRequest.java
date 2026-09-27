package com.example.short_link.event.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record ChangeStatusRequest(@NotBlank String action) {}
