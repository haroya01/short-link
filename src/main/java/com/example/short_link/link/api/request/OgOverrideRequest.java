package com.example.short_link.link.api.request;

import jakarta.validation.constraints.Size;

public record OgOverrideRequest(
    @Size(max = 300) String ogTitle,
    @Size(max = 800) String ogDescription,
    @Size(max = 1024) String ogImage) {}
