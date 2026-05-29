package com.example.short_link.user.application.dto;

/** A freshly minted access token plus its lifetime in seconds (for an admin-issued API token). */
public record MintedAccessToken(String accessToken, long expiresInSeconds) {}
