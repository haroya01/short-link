package com.example.short_link.link.presentation.response;

import com.example.short_link.link.domain.ShortCode;

public record CreateLinkResponse(ShortCode shortCode, String shortUrl, String claimToken) {}
