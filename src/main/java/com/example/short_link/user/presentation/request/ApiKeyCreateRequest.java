package com.example.short_link.user.presentation.request;

import jakarta.validation.constraints.Size;

public record ApiKeyCreateRequest(@Size(max = 100) String name) {}
