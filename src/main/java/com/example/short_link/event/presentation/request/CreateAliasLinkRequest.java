package com.example.short_link.event.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAliasLinkRequest(@NotBlank @Size(max = 50) String label) {}
