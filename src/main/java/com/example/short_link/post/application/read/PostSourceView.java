package com.example.short_link.post.application.read;

public record PostSourceView(
    String platform, String originalUrl, String shortUrl, boolean excerptOnly) {}
