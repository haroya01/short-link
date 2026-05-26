package com.example.short_link.link.access.presentation.response;

import com.example.short_link.link.domain.ShortCode;

public record LinkVisibilityResponse(ShortCode shortCode, boolean statsPublic) {}
