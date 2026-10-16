package com.example.short_link.federation.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RelaySubscribeRequest(@NotBlank @Size(max = 512) String actorUri) {}
