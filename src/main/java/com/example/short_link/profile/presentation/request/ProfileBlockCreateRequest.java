package com.example.short_link.profile.presentation.request;

import jakarta.validation.constraints.Size;

public record ProfileBlockCreateRequest(String type, @Size(max = 120) String content) {}
