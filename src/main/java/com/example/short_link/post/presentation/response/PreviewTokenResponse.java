package com.example.short_link.post.presentation.response;

/** The share token for an owned post; the client builds the {@code {slug}?preview={token}} link. */
public record PreviewTokenResponse(String token) {}
