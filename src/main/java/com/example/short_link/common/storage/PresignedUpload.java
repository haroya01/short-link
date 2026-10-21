package com.example.short_link.common.storage;

public record PresignedUpload(
    String uploadUrl,
    String publicUrl,
    String key,
    String contentType,
    long maxBytes,
    long expiresIn) {}
