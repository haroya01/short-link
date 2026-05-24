package com.example.short_link.profile.api.request;

import jakarta.validation.constraints.Size;

public record ProfileBlockUpdateRequest(@Size(max = 120) String content) {}
